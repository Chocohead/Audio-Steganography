package audiosteganography.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public final class AudioUtils {
	private AudioUtils() {
	}

	/**
	 * Writes the given {@link AudioStream} to the given {@link File}
	 * <p>
	 * Presumes that the audio stream is a single (mono) channel, 44100 kHz stream with a 16 bit sample size
	 * </p>
	 *
	 * @param to The file to save the given audio to
	 * @param stream The audio stream to save in the given file
	 *
	 * @throws IOException If an I/O Exception occurs
	 */
	public static void write(File to, AudioStream stream) throws IOException {
		write(to, stream, 1, 44100, 16);
	}

	/**
	 * Writes the given {@link AudioStream} to the given {@link File}
	 *
	 * @param to The file to save the given audio to
	 * @param stream The audio stream to save in the given file
	 * @param channels The number of audio channels in the stream
	 * @param sampleRate The stream's sample rate
	 * @param sampleSizeInBits The stream's sample size (in bits)
	 *
	 * @throws IOException If an I/O Exception occurs
	 */
	public static void write(File to, AudioStream stream, int channels, float sampleRate, int sampleSizeInBits) throws IOException {
		String fileName = to.getName();

		Type fileType;
		boolean bigEndian;
		if (fileName.endsWith(".au")) {
			fileType = Type.AU;
			bigEndian = true;
		} else if (fileName.endsWith(".wav")) {
			fileType = Type.WAVE;
			bigEndian = false;
		} else if (fileName.endsWith(".aif") || fileName.endsWith(".aiff")) {
			fileType = Type.AIFF;
			bigEndian = true;
		} else {
			throw new IllegalArgumentException("Unrecognised file format for " + fileName);
		}

		write(to, stream, fileType, new AudioFormat(sampleRate, sampleSizeInBits, channels, true, bigEndian));
	}

	/**
	 * Writes the given {@link AudioStream} to the given {@link File}
	 *
	 * @param to The file to save the given audio to
	 * @param stream The audio stream to save in the given file
	 * @param fileType The type the file will be saved as
	 * @param format The format of the stream's audio
	 *
	 * @throws IOException If an I/O Exception occurs
	 */
	public static void write(File to, AudioStream stream, Type fileType, AudioFormat format) throws IOException {
		AudioInputStream audio = new AudioInputStream(new InputStream() {
			private final int sampleSize = format.getSampleSizeInBits() / 8;
			private final boolean bigEndian = format.isBigEndian();

			private byte[] extra = new byte[0];

			private int ensureHas(int samples) throws IOException {
				int needed = samples - extra.length;
				if (needed <= 0) return samples;

				float[] newSamples = new float[needed];

				int read = stream.next(newSamples);
				if (read == -1) return 0;

				byte[] buffer = new byte[needed * sampleSize];
				for (int i = 0; i < newSamples.length; i++) {
					switch(sampleSize) {
					case 1: {// 8 bit
						buffer[i] = new Float(newSamples[i] * Byte.MAX_VALUE).byteValue();
						break;
					}

					case 2: {// 16 bit
						short sval = new Float(newSamples[i] * Short.MAX_VALUE).shortValue();
						if (bigEndian) {
							buffer[i * 2] = (byte) ((sval & 0x0000FF00) >> 8);
							buffer[i * 2 + 1] = (byte) (sval & 0x000000FF);
						} else {
							buffer[i * 2] = (byte) (sval & 0x000000FF);
							buffer[i * 2 + 1] = (byte) ((sval & 0x0000FF00) >> 8);
						}
						break;
					}

					case 3: {// 24 bit
						int ival = new Float(newSamples[i] * 8388608F).intValue();
						if (bigEndian) {
							buffer[i * 3] = (byte) ((ival & 0x00FF0000) >> 8 * 2);
							buffer[i * 3 + 1] = (byte) ((ival & 0x0000FF00) >> 8);
							buffer[i * 3 + 2] = (byte) (ival & 0x000000FF);
						} else {
							buffer[i * 3] = (byte) (ival & 0x000000FF);
							buffer[i * 3 + 1] = (byte) ((ival & 0x0000FF00) >> 8);
							buffer[i * 3 + 2] = (byte) ((ival & 0x00FF0000) >> 8 * 2);
						}
						break;
					}

					case 4: {// 32 bit
						int ival = new Float(newSamples[i] * Integer.MAX_VALUE).intValue();
						if (bigEndian) {
							buffer[i * 4] = (byte) ((ival & 0xFF000000) >> 8 * 3);
							buffer[i * 4 + 1] = (byte) ((ival & 0x00FF0000) >> 8 * 2);
							buffer[i * 4 + 2] = (byte) ((ival & 0x0000FF00) >> 8);
							buffer[i * 4 + 3] = (byte) (ival & 0x000000FF);
						} else {
							buffer[i * 4] = (byte) (ival & 0x000000FF);
							buffer[i * 4 + 1] = (byte) ((ival & 0x0000FF00) >> 8);
							buffer[i * 4 + 2] = (byte) ((ival & 0x00FF0000) >> 8 * 2);
							buffer[i * 4 + 3] = (byte) ((ival & 0xFF000000) >> 8 * 3);
						}
						break;
					}

					default:
						throw new IllegalStateException("Unsupported sample size: " + sampleSize);
					}
				}

				byte[] lengthened = new byte[extra.length + buffer.length];
				System.arraycopy(extra, 0, lengthened, 0, extra.length);
				System.arraycopy(buffer, 0, lengthened, extra.length, buffer.length);
				extra = lengthened;

				return read / sampleSize;
			}

			@Override
			public synchronized int read() throws IOException {
				if (ensureHas(1) == 0) {
					return -1;
				} else {
					byte out = extra[0];
					extra = Arrays.copyOfRange(extra, 1, extra.length);
					return out & 0xFF;
				}
			}

			@Override
			public synchronized int read(byte[] b, int off, int len) throws IOException {
				if (b == null) {
					throw new NullPointerException();
				} else if (off < 0 || len < 0 || len > b.length - off) {
					throw new IndexOutOfBoundsException();
				} else if (len == 0) {
					return 0;
				}

				//Ensure there is still something to read
				len = Math.min(ensureHas(len) * sampleSize, len);
				if (len == 0) return -1;

				System.arraycopy(extra, 0, b, off, len);
				extra = Arrays.copyOfRange(extra, len, extra.length);

				return len;
			}
		}, format, stream.getLength() / format.getChannels());
		AudioSystem.write(audio, fileType, to);
	}
}