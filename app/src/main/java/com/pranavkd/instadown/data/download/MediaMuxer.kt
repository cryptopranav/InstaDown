package com.pranavkd.instadown.data.download

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaMuxer @Inject constructor() {

    fun muxVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File
    ): Result<Unit> {
        return runCatching {
            val videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            val audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

            val videoTrackIndex = selectTrack(videoExtractor, "video/")
            val audioTrackIndex = selectTrack(audioExtractor, "audio/")

            if (videoTrackIndex < 0 || audioTrackIndex < 0) {
                throw IllegalStateException("Required tracks not found")
            }

            videoExtractor.selectTrack(videoTrackIndex)
            audioExtractor.selectTrack(audioTrackIndex)

            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)

            val muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            val muxedVideoTrack = muxer.addTrack(videoFormat)
            val muxedAudioTrack = muxer.addTrack(audioFormat)

            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()

            // Write video samples
            while (true) {
                info.set(0, 0, 0, 0)
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.offset = 0
                info.size = sampleSize
                info.flags = videoExtractor.sampleFlags
                info.presentationTimeUs = videoExtractor.sampleTime
                muxer.writeSampleData(muxedVideoTrack, buffer, info)
                videoExtractor.advance()
            }

            // Write audio samples
            while (true) {
                info.set(0, 0, 0, 0)
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.offset = 0
                info.size = sampleSize
                info.flags = audioExtractor.sampleFlags
                info.presentationTimeUs = audioExtractor.sampleTime
                muxer.writeSampleData(muxedAudioTrack, buffer, info)
                audioExtractor.advance()
            }

            muxer.stop()
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }
}
