package com.ricardofrango.squadify.util

import com.ricardofrango.squadify.exception.ValidationException
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LogoImageTest {
    private fun image(width: Int, height: Int, format: String): ByteArray {
        val type = if (format == "png") BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val img = BufferedImage(width, height, type)
        img.createGraphics().apply { color = Color.RED; fillRect(0, 0, width, height); dispose() }
        return ByteArrayOutputStream().use { ImageIO.write(img, format, it); it.toByteArray() }
    }

    private fun read(png: ByteArray) = ImageIO.read(ByteArrayInputStream(png))

    @Test
    fun aBigWidePhotoIsFittedInto512OnATransparentSquare() {
        val out = read(LogoImage.toPng(image(2000, 1000, "jpg")))
        assertEquals(512, out.width)
        assertEquals(512, out.height)
        // Centred: the band above the 512×256 picture stays transparent, the middle is the picture.
        assertEquals(0, out.getRGB(256, 10) ushr 24)
        assertEquals(255, out.getRGB(256, 256) ushr 24)
    }

    @Test
    fun aSmallLogoIsNotEnlarged() {
        val out = read(LogoImage.toPng(image(200, 100, "png")))
        assertEquals(200, out.width)
        assertEquals(200, out.height)
    }

    @Test
    fun onlyPngAndJpegAreAccepted() {
        assertFailsWith<ValidationException> { LogoImage.toPng(image(50, 50, "gif")) }
        assertFailsWith<ValidationException> { LogoImage.toPng("not an image".toByteArray()) }
        assertFailsWith<ValidationException> { LogoImage.toPng(ByteArray(0)) }
    }

    @Test
    fun moreThan5MbOrAHugePictureIsRefused() {
        assertFailsWith<ValidationException> { LogoImage.toPng(ByteArray(LogoImage.MAX_UPLOAD_BYTES + 1)) }
        assertFailsWith<ValidationException> { LogoImage.toPng(image(7000, 10, "png")) }
    }
}
