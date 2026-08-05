package com.checkingcontainer.feature.units

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prepara una foto de la galería o la cámara para subirla: la reescala a un
 * máximo de [MAX_PHOTO_DIM] px y la comprime a JPEG.
 *
 * Vive fuera del ViewModel a propósito. Decodificar bitmaps, calcular el
 * submuestreo y corregir la rotación EXIF no es coordinar estado de pantalla:
 * es tratamiento de imagen, y mezclarlo con el ViewModel del estimado hacía ese
 * archivo más grande y ataba ~90 líneas de lógica de imagen a un `Context` que
 * no se podía separar para probar.
 */
@Singleton
class CompresorDeFotos @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Devuelve los bytes JPEG listos para subir, o null si la imagen no se pudo
     * leer o decodificar (el llamador decide si sube los bytes originales).
     *
     * La cámara entrega 3-6 MB por foto; sin este paso se subían crudos a Storage.
     * Se aplica la rotación EXIF porque al recodificar se pierde ese metadato.
     */
    fun comprimir(uri: Uri): ByteArray? = runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO_DIM) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        bmp = aplicarRotacionExif(uri, bmp)
        bmp = escalarAlMaximo(bmp)

        ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bmp.recycle()
            out.toByteArray()
        }
    }.getOrNull()

    /**
     * EXIF es mejor-esfuerzo: PNG/HEIC u otros formatos pueden lanzar al leer los
     * metadatos, y eso NO debe descartar la foto (bug: "No se pudo leer la foto").
     */
    private fun aplicarRotacionExif(uri: Uri, bmp: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bmp

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        if (rotated !== bmp) bmp.recycle()
        return rotated
    }

    /**
     * `inSampleSize` solo reduce por potencias de 2: una foto de 4000px quedaba en
     * 2000px y pesaba 1-3 MB (lento de subir Y de volver a bajar — fotos "que no se
     * ven" con datos móviles). Este escalado final garantiza ≤ [MAX_PHOTO_DIM] px.
     */
    private fun escalarAlMaximo(bmp: Bitmap): Bitmap {
        val mayor = maxOf(bmp.width, bmp.height)
        if (mayor <= MAX_PHOTO_DIM) return bmp

        val factor = MAX_PHOTO_DIM.toFloat() / mayor
        val scaled = Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * factor).toInt().coerceAtLeast(1),
            (bmp.height * factor).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    private companion object {
        const val MAX_PHOTO_DIM = 1600
        const val JPEG_QUALITY = 80
    }
}
