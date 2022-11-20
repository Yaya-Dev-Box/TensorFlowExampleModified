/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.imagesegmentation.fragments

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.ExifInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.github.dhaval2404.imagepicker.ImagePicker
import kotlinx.coroutines.delay
import org.tensorflow.lite.examples.imagesegmentation.ImageSegmentationHelper
import org.tensorflow.lite.examples.imagesegmentation.ImageSegmentationHelper.SegmentationListener
import org.tensorflow.lite.examples.imagesegmentation.OverlayView
import org.tensorflow.lite.examples.imagesegmentation.OverlayView.ColorLabel
import org.tensorflow.lite.examples.imagesegmentation.R
import org.tensorflow.lite.examples.imagesegmentation.databinding.FragmentCameraBinding
import org.tensorflow.lite.task.vision.segmenter.Segmentation


class CameraFragment : Fragment(), SegmentationListener {

    companion object {
        private const val TAG = "Image Segmentation"

    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var imageSegmentationHelper: ImageSegmentationHelper
    private lateinit var bitmapBuffer: Bitmap
    private val labelsAdapter: ColorLabelsAdapter by lazy { ColorLabelsAdapter() }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(CameraFragmentDirections.actionCameraToPermissions())
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageSegmentationHelper = ImageSegmentationHelper(
            context = requireContext(),
            imageSegmentationListener = this
        )

        with(fragmentCameraBinding.recyclerviewResults) {
            adapter = labelsAdapter
            layoutManager = GridLayoutManager(requireContext(), 3)
        }

        fragmentCameraBinding.overlay.setOnOverlayViewListener(object : OverlayView.OverlayViewListener {
            override fun onLabels(colorLabels: List<ColorLabel>) {
                // update label at here
                labelsAdapter.updateResultLabels(colorLabels)
            }
        })

        setOnClickListeners()
    }

    private fun segmentImage(bitmap: Bitmap,rotationInDegrees : Int) {
        // Copy out RGB bits to the shared bitmap buffer
        bitmapBuffer = Bitmap.createBitmap(bitmap)

        // Pass Bitmap and rotation to the image segmentation helper for processing and segmentation
        imageSegmentationHelper.segment(bitmapBuffer, rotationInDegrees)
    }

    private val startForCapturePhotoResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val image = it.data?.data ?: return@registerForActivityResult
        _fragmentCameraBinding?.imageView?.load(image) { allowHardware(false) }

        val exif = ExifInterface(image.path.orEmpty())
        val rotation: Int = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val rotationInDegrees: Int = exifToDegrees(rotation)

        lifecycleScope.launchWhenStarted {
            delay(1000)
            val bitmap = _fragmentCameraBinding?.imageView?.drawable?.toBitmap(config = Bitmap.Config.ARGB_8888)
            segmentImage(bitmap ?: return@launchWhenStarted,rotationInDegrees)
        }
    }

    private fun exifToDegrees(exifOrientation: Int): Int {
        return when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    private fun setOnClickListeners() {
        _fragmentCameraBinding?.cameraBtn?.setOnClickListener { view ->
            ImagePicker.with(this).cameraOnly().createIntent { startForCapturePhotoResult.launch(it) }
        }
    }

    // Update UI after objects have been segment. Extracts original image height/width
    // to scale and place bounding boxes properly through OverlayView
    override fun onResults(
        results: List<Segmentation>?,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        activity?.runOnUiThread {
            // Pass necessary information to OverlayView for drawing on the canvas
            fragmentCameraBinding.overlay.setResults(
                results,
                imageHeight,
                imageWidth
            )

            // Force a redraw
            fragmentCameraBinding.overlay.invalidate()
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }
}
