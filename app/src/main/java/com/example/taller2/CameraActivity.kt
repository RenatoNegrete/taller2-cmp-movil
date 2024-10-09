package com.example.taller2

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.taller2.databinding.ActivityCameraBinding
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.Logger

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding

    companion object {
        val TAG: String = CameraActivity::class.java.name
    }
    private val logger = Logger.getLogger(TAG)

    private val getSimplePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) {
        updateUI(it)
    }

    var pictureImagePath: Uri? = null
    var videoPath: Uri? = null
    var imageViewContainer: ImageView? = null
    var videoViewContainer: VideoView? = null

    private val cameraActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val videoEnabled = binding.videoSwitch.isChecked

            if (videoEnabled) {
                imageViewContainer!!.visibility = View.GONE
                videoViewContainer!!.visibility = View.VISIBLE
                videoViewContainer!!.setVideoURI(videoPath)
                videoViewContainer!!.setOnPreparedListener {
                    videoViewContainer!!.start()
                }
                logger.info("Video recorded successfully")
            } else {
                videoViewContainer!!.visibility = View.GONE
                imageViewContainer!!.visibility = View.VISIBLE
                imageViewContainer!!.setImageURI(null)
                val newUri = Uri.parse(pictureImagePath.toString() + "?time=" + System.currentTimeMillis())
                imageViewContainer!!.setImageURI(newUri)
                imageViewContainer!!.setScaleType(ImageView.ScaleType.FIT_CENTER)
                imageViewContainer!!.setAdjustViewBounds(true)
                logger.info("Image capture successfully")
            }
        } else {
            logger.warning("Capture failed")
        }
    }

    private val galleryActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = result.data!!.data
            val videoEnabled = binding.videoSwitch.isChecked
            if(videoEnabled) {
                imageViewContainer!!.visibility  = View.GONE
                videoViewContainer!!.visibility = View.VISIBLE
                videoViewContainer!!.setVideoURI(uri)
                videoViewContainer!!.setOnPreparedListener {
                    videoViewContainer!!.start()
                }
                logger.info("Video loaded successfully")
            } else {
                videoViewContainer!!.visibility = View.GONE
                imageViewContainer!!.visibility = View.VISIBLE
                imageViewContainer!!.setImageURI(uri)
                logger.info("Image loadead successfully")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageViewContainer = binding.imageView
        videoViewContainer = binding.videoView

        logger.info("Se va a solicitar el permiso")

        binding.buttonTake.setOnClickListener {
            val videoEnabled = binding.videoSwitch.isChecked
            if (!videoEnabled) {
                verifyPermissions(this, android.Manifest.permission.CAMERA, "El permiso es requerido para capturar la foto")
            } else {
                verifyPermissions(this, android.Manifest.permission.CAMERA, "El permiso es requerido para grabar el video")
            }
        }

        binding.buttonGalery.setOnClickListener {
            val videoEnabled = binding.videoSwitch.isChecked
            val pickGalleryIntent = Intent(Intent.ACTION_PICK)
            if (videoEnabled) {
                pickGalleryIntent.type = "video/*"
            } else {
                pickGalleryIntent.type = "image/*"
            }
            galleryActivityResultLauncher.launch(pickGalleryIntent)
        }
    }

    private fun verifyPermissions(context: Context, permission: String, rationale: String) {
        when {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> {
                Snackbar.make(binding.root, "Ya tengo los permisos", Snackbar.LENGTH_SHORT).show()
                updateUI(true)
            }
            shouldShowRequestPermissionRationale(permission) -> {
                val snackbar = Snackbar.make(binding.root, rationale, Snackbar.LENGTH_SHORT)
                snackbar.addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(snackbar: Snackbar, event: Int) {
                        if (event == DISMISS_EVENT_TIMEOUT) {
                            getSimplePermission.launch(permission)
                        }
                    }
                })
                snackbar.show()
            }
            else -> {
                getSimplePermission.launch(permission)
            }
        }
    }

    fun updateUI(permission: Boolean) {
        if (permission) {
            val videoEnabled = binding.videoSwitch.isChecked
            logger.info("Permission granted")
            if (videoEnabled) {
                dispatchTakeVideoIntent()
            } else {
                dispatchTakePictureIntent()
            }
        } else {
            logger.warning("Permission denied")
        }
    }

    fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        var imageFile: File? = null
        try {
            imageFile = createImageFile()
        } catch (ex: IOException) {
            logger.warning(ex.message)
        }

        if (imageFile != null) {
            pictureImagePath = FileProvider.getUriForFile(this,"com.example.android.fileprovider", imageFile)
            logger.info("Ruta: $pictureImagePath")
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, pictureImagePath)
            try {
                cameraActivityResultLauncher.launch(takePictureIntent)
            } catch (e: ActivityNotFoundException) {
                logger.warning("Camera app not found")
            }
        }
    }

    fun dispatchTakeVideoIntent() {
        val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        var videoFile: File? = null
        try {
            videoFile = createVideoFile()
        } catch (ex: IOException) {
            logger.warning(ex.message)
        }

        if (videoFile != null) {
            videoPath = FileProvider.getUriForFile(this, "com.example.android.fileprovider", videoFile)
            logger.info("Ruta: $videoPath")
            takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoPath)
            try {
                cameraActivityResultLauncher.launch(takeVideoIntent)
            } catch (e: ActivityNotFoundException) {
                logger.warning("Camera app not found")
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}.jpg"
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(storageDir, imageFileName)
    }

    @Throws(IOException::class)
    private fun createVideoFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val videoFileName = "VIDEO_${timeStamp}.mp4"
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File(storageDir, videoFileName)
    }
}