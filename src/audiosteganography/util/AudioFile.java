package audiosteganography.util;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public class AudioFile {
	/** The file being read from */
	protected final File file;
	/** The format of {@link #file} */
	protected final AudioFileFormat fileFormat;
	/** The format of {@link #fileFormat} */
	protected final AudioFormat format;
	/** Whether the file data little or big endian */
	protected final boolean bigEndian;
	/** The number of channels in the file (1 == mono, 2 = stereo, etc.) */
	protected final int channels;
	/** The file's sample rate as samples per second */
	protected final float sampleRate;
	/** The number of samples in the file */
	protected final long duration;
	/** The number of bytes long each sample is (1 = 8 bit, 2 = 16 bit, etc.) */
	protected final int sampleSize;

	/**
	 * Creates a new {@link AudioFile} for the given file path, preparing it to be read.
	 *
	 * @param fileName The pathname of the file to be read
	 *
	 * @throws UnsupportedAudioFileException
	 * @throws IOException
	 */
	public AudioFile(String fileName) throws UnsupportedAudioFileException, IOException {
		this(new File(fileName));
	}

	/**
	 * Creates a new {@link AudioFile} for the given file, preparing it to be read.
	 *
	 * @param file The file to be read
	 *
	 * @throws IOException If an I/O Exception occurs reading the <code>File</code>
	 * @throws UnsupportedAudioFileException If the <code>File</code> does not point to valid audio file data recognised by the system
	 */
	public AudioFile(File file) throws UnsupportedAudioFileException, IOException {
		this.file = file;
		fileFormat = AudioSystem.getAudioFileFormat(this.file);
		format = fileFormat.getFormat();
		bigEndian = format.isBigEndian();
		channels = format.getChannels();
		sampleRate = format.getSampleRate();
		duration = (long) fileFormat.getFrameLength() * channels;
		sampleSize = format.getSampleSizeInBits() / 8;
	}

	/**
	 * Get an {@link AudioStream} of the audio contained in the underlying file
	 *
	 * @return A stream of the audio
	 *
	 * @throws IOException If there is I/O Exception occurs creating the stream
	 * @throws UnsupportedAudioFileException In the unlikely event that the system recognises the audio format but not the contents
	 */
	public AudioStream getSampleStream() throws UnsupportedAudioFileException, IOException {
		return new AudioStream() {
			/** Audio input stream, will hold the byte array of sample data */
			private final AudioInputStream is = AudioSystem.getAudioInputStream(file);
			/** The size of each frame, in bytes */
			private final int frameSize = Math.max(format.getFrameSize(), sampleSize);
			/** Buffered (potentially partial) frames from {@link #is} */
			private byte[] extra = new byte[0];

			@Override
			public long getLength() {
				return is.getFrameLength() * (frameSize / sampleSize);
			}

			/**
			 * Add the given frames to {@link #extra}
			 *
			 * @param frames The additional frames to buffer up
			 */
			private void buffer(byte[] frames) {
				byte[] lengthened = new byte[extra.length + frames.length];
				System.arraycopy(extra, 0, lengthened, 0, extra.length);
				System.arraycopy(frames, 0, lengthened, extra.length, frames.length);
				extra = lengthened;
			}

			@Override
			public float next() throws IOException {
				if (extra.length < sampleSize) {
					byte[] frame = new byte[frameSize];

					if (is.read(frame) == -1) {
						throw new EOFException("Reached end of stream");
					}

					buffer(frame);
				}

				byte[] sample = Arrays.copyOf(extra, sampleSize);
				extra = Arrays.copyOfRange(extra, sampleSize, extra.length);

				return getFloat(sample);
			}

			@Override
			public int next(float[] buffer) throws IOException {
				int samplesNeeded = sampleSize * buffer.length;

				int read;
				if (extra.length < samplesNeeded) {
					int framesNeeded = frameSize * (int) Math.ceil(sampleSize * buffer.length / (double) frameSize);
					byte[] frames = new byte[framesNeeded];

					read = Math.min(is.read(frames), samplesNeeded * sampleSize);

					buffer(frames);
				} else {
					read = samplesNeeded * sampleSize;
				}

				byte[] samples = Arrays.copyOf(extra, samplesNeeded);
				extra = Arrays.copyOfRange(extra, samplesNeeded, extra.length);

				try (ByteArrayInputStream bis = new ByteArrayInputStream(samples)) {
					byte[] sample = new byte[sampleSize];

					for (int i = 0; i < buffer.length; i++) {
						if (bis.read(sample) == -1) break;
						buffer[i] = getFloat(sample);
					}
				}

				return read;
			}

			@Override
			public float[] all() throws IOException {
				try {
					float[] buffer = new float[Math.toIntExact(getLength())];
					next(buffer);
					return buffer;
				} catch (ArithmeticException e) {
					throw new IllegalArgumentException("Audio stream too long to fit in array", e);
				}
			}

			/**
			 * Interpret the given sample as a float value
			 *
			 * @param b The sample to be converted into a float
			 *
			 * @return The value of the given sample as a float
			 */
			private float getFloat(byte[] b) {
				int ret = 0;
				for(int i = 0, length = b.length; i < b.length; i++, length--) {
					ret |= (b[i] & 0xFF) << (bigEndian ? length : i + 1) * 8 - 8;
				}

				float sample;
				switch(sampleSize) {
				case 1:
					if (ret > 0x7F) {
						ret = ~ret;
						ret &= 0x7F;
						ret = ~ret + 1;
					}
					sample = (float) ret / (float) Byte.MAX_VALUE;
					break;

				case 2:
					if (ret > 0x7FFF) {
						ret = ~ret;
						ret &= 0x7FFF;
						ret = ~ret + 1;
					}
					sample = (float) ret / (float) Short.MAX_VALUE;
					break;

				case 3:
					if (ret > 0x7FFFFF) {
						ret = ~ret;
						ret &= 0x7FFFFF;
						ret = ~ret + 1;
					}
					sample = ret / 8388608F;
					break;

				case 4:
					sample = (float) ((double) ret / (double) Integer.MAX_VALUE);
					break;

				default:
					throw new IllegalStateException("Unsupported sample size: " + sampleSize);
				}

				return sample;
			}

			@Override
			public void close() throws IOException {
				is.close();
				extra = null;
			}
		};
	}

	/**
	 * Gets the bit size of the audio
	 *
	 * @return The bit depth, will be either 8, 16, 24, or 32
	 */
	public int getBitResolution() {
		int depth;
		switch (sampleSize) {
		case 1:
			depth = 8;
			break;

		case 2:
			depth = 16;
			break;

		case 3:
			depth = 24;
			break;

		case 4:
			depth = 32;
			break;

		default:
			throw new IllegalStateException("Unsupported sample size: " + sampleSize);
		}

		return depth;
	}

	/**
	 * Gets the number of channels in the underlying audio file
	 *
	 * @return The number of audio channels (1 = mono, 2 = stereo, etc.)
	 */
	public int getChannels() {
		return channels;
	}

	/**
	 * Gets the format of the underlying audio file
	 *
	 * @return The format of the underlying audio file
	 */
	public AudioFileFormat getFileFormat() {
		return fileFormat;
	}

	/**
	 * Gets the type of underlying audio file (wav, aif, au, etc.)
	 *
	 * @return The type of underlying audio file
	 */
	public Type getFileType() {
		return fileFormat.getType();
	}

	/**
	 * Gets the sample rate of the audio
	 *
	 * @return The number of samples per second
	 */
	public float getSampleRate() {
		return sampleRate;
	}


	/**
	 * Gets the sample size of the audio as a number of bits per sample.
	 *
	 * @return The number of bits per sample (8, 16, 24, 32, etc.)
	 */
	public int getSampleBitDepth() {
		return sampleSize * 8;
	}

	/**
	 * Gets the type of encoding used by the underlying audio file
	 *
	 * @return The encoding type of the underlying audio file
	 */
	public Encoding getEncoding() {
		return format.getEncoding();
	}

	/**
	 * Gets the number of samples for the given {@link AudioFile}
	 *
	 * @return The total number of samples in the file
	 */
	public long getDuration() {
		return duration;
	}
}
