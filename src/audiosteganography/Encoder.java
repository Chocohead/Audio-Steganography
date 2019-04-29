package audiosteganography;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.UnsupportedAudioFileException;

import audiosteganography.audio.AudioSampleWriter;
import audiosteganography.audio.AudioTool;
import audiosteganography.binary.BinaryTool;
import audiosteganography.fourier.Complex;
import audiosteganography.fourier.FFT;
import audiosteganography.fourier.FFTData;
import audiosteganography.fourier.FFTDataAnalyzer;
import audiosteganography.util.AudioFile;
import audiosteganography.util.AudioStream;

public class Encoder {
	final File audioFile;

	public Encoder(File audioFile) {
		this.audioFile = audioFile;
	}

	public void encodeMessage(String message, String outPath) { //change outPath to File
		int[] messageAsBits = BinaryTool.ASCIIToBinary(message).getIntArray();
		boolean[] messageAsBinary = new boolean[messageAsBits.length];

		for (int i = 0; i < messageAsBits.length; i++) {
			messageAsBinary[i] = messageAsBits[i] == 1;
		}

		encodeMessage(messageAsBinary, new File(outPath));
	}

	public void encodeMessage(boolean[] messageAsBits, File to) {
		try {
			AudioFile audioFile = new AudioFile(this.audioFile);

			try (AudioStream audio = audioFile.getSampleStream(); AudioSampleWriter writer = new AudioSampleWriter(to, audioFile.getFormat(), Type.WAVE)) {
				long bytesRead = 0;
				long totalBytes = audio.getLength();

				int bytesToRead = 4096 * 2; //some arbitrary number thats 2^n
				if (totalBytes / bytesToRead < messageAsBits.length) {
					throw new RuntimeException("The audio file is too short for the message to fit!");
				}

				int currentBit = 0;
				while (bytesRead < totalBytes && currentBit < messageAsBits.length) {
					if (totalBytes - bytesRead < bytesToRead) {
						//If the remaining bytes are less than bytesToRead there will be no overflow casting back to an int
						bytesToRead = (int) (totalBytes - bytesRead);
					}

					//System.out.println("Reading data.");
					//take a portion of the data
					float[] rawSamples = new float[bytesToRead];
					audio.next(rawSamples);

					double[] samples = new double[bytesToRead];
					for (int i = 0; i < samples.length; i++) {
						samples[i] = rawSamples[i];
					}
					bytesRead += bytesToRead;

					double[] channelOne = new double[samples.length / 2];
					for (int i = 0; i < samples.length; i += 2) {
						channelOne[i / 2] = samples[i];
					}

					//System.out.println("Taking the FFT.");
					//take the FFT
					FFTData[] freqMag = FFT.getMag(channelOne, 44100); // TODO: don't hardcode
					FFTDataAnalyzer analyzer = new FFTDataAnalyzer(freqMag);
					boolean isRest = analyzer.isRest();

					channelOne = FFT.correctDataLength(channelOne);
					Complex[] complexData = new Complex[channelOne.length];
					for (int i = 0; i < channelOne.length; i++) {
						complexData[i] = new Complex(channelOne[i], 0);
					}
					Complex[] complexMags = FFT.fft(complexData);
					double[] freqs = FFT.getFreqs(complexData.length, 44100); // TODO: don't hardcode

					//System.out.println("Writing the 1 or 0");
					//decide if the overtone should be changed and if so, change it. don't write if its a rest
					if (!isRest) {
						if (messageAsBits[currentBit]) {
							//edit the data thats going to be ifft'd
							for (int i = 0; i < freqs.length; i++) {
								if (Math.abs(Math.abs(freqs[i]) - 20000) < 5) { //lets try changing a set freq
									complexMags[i] = new Complex(15, 0); // don't hardcode
								}
							}

							//take the IFFT
							Complex[] ifft = FFT.ifft(complexMags);

							//change ifft data from complex to real. put in fft class?
							double[] ifftReal = Arrays.stream(ifft).mapToDouble(Complex::re).toArray();

							double[] toWrite = AudioTool.interleaveSamples(ifftReal);
							writer.write(toWrite); //add to the array thats going to be written out
						} else {
							//add a 0 to the message
							writer.write(samples);
						}
						currentBit++;
					} else {// similar to encoding a zero, but don't increment the bit count
						writer.write(samples);
					}
				}

				//writing out the leftover part of the audio file (which doesn't have any encoded btis in it)
				if (bytesRead < totalBytes) {
					float[] buffer = new float[bytesToRead];

					int read;
					while ((read = audio.next(buffer)) != -1) {
						double[] extra = new double[read / (audioFile.getSampleBitDepth() / 8)];

						//take a portion of the data
						for (int i = 0; i < extra.length; i++) {
							extra[i] = buffer[i];
						}
						for (int i = extra.length; i < buffer.length; i++) {
							if (buffer[i] != 0) {
								throw new AssertionError("Dropped some of extra buffer: " + Arrays.copyOfRange(buffer, extra.length, buffer.length) + " after reading " + read);
							}
						}

						writer.write(extra);
					}
				}
			}
		} catch (IOException | UnsupportedAudioFileException e) {
			e.printStackTrace();
		}
	}

	public static void main(String args[]) {
		String message = args[0];
		String filePath = args[1];
		String outPath = filePath.substring(0,filePath.length()-4)+"-Encoded.wav";
		Encoder encoder = new Encoder(new File(filePath));
		encoder.encodeMessage(message,outPath);
		System.out.println("Successfully encoded \"" + message + "\" into " + outPath);
	}
}
