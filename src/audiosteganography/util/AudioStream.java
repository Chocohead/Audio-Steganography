package audiosteganography.util;

import java.io.Closeable;
import java.io.IOException;

public interface AudioStream extends Closeable {
	/**
	 * Gets the length of the stream, expressed as number of sample frames (rather than bytes)
	 *
	 * @return The length of the stream in sample frames
	 */
	long getLength();

	/**
	 * Gets the next frame of the stream
	 *
	 * @return The next frame of the stream
	 *
	 * @throws IOException If there is an I/O Exception reading the stream or the end of the stream has been reached
	 */
	float next() throws IOException;

	/**
	 * Get the appropriate number of frames to either fill buffer or each the end of the stream
	 *
	 * @param buffer The buffer to fill with frames
	 *
	 * @return The number of bytes written to buffer, -1 if the stream has ended
	 *
	 * @throws IOException If there is an I/O Exception reading from the stream
	 */
	int next(float[] buffer) throws IOException;

	/**
	 * Read all remaining frames from the buffer
	 *
	 * @return An array of all remaining frames from the buffer
	 *
	 * @throws IllegalArgumentException If the number of remaining frames is greater than {@link Integer#MAX_VALUE}
	 * @throws IOException If there is an I/O Exception reading from the stream
	 *
	 * @see #getLength() AudioStream.getLength()
	 */
	float[] all() throws IOException;
}