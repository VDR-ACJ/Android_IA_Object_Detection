package com.alura.aifound.ui.camera

import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.alura.aifound.data.Product
import com.alura.aifound.extensions.dpToPx
import com.alura.aifound.extensions.pxToDp
import com.alura.aifound.sampleData.ProductSample
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
//import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.PredefinedCategory


@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScreen(
    onNewProductDetected: (Product) -> Unit
) {
    val viewModel = hiltViewModel<CameraViewModel>()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current.applicationContext

    val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableClassification()  // Optional
        .build()

    val localModel = LocalModel.Builder()
        //https://www.kaggle.com/models?publisher=google
        .setAssetFilePath("model_products.tflite")
        // or .setAbsoluteFilePath(absolute file path to model file)
        // or .setUri(URI to model file)
        .build()

    val customObjectDetectorOptions =
        CustomObjectDetectorOptions.Builder(localModel)
            .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .setClassificationConfidenceThreshold(0.5f)
            .setMaxPerObjectLabelCount(3)
            .build()

    val objectDetector = remember { ObjectDetection.getClient(customObjectDetectorOptions) }

    var boundingBox by remember {
        mutableStateOf(Rect(0f, 0f, 0f, 0f))
    }
    var coordinateX by remember {
        mutableStateOf(0.dp)
    }

    var coordinateY by remember {
        mutableStateOf(0.dp)
    }

    var imageSize by remember {
        mutableStateOf(Size(1,1))
    }
    var screenSize by remember {
        mutableStateOf(Size(1,1))
    }

    coordinateX = (boundingBox.topLeft.x / imageSize.width * screenSize.width).pxToDp()
    coordinateY = (boundingBox.topLeft.y / imageSize.height * screenSize.height).pxToDp()

    val cameraAnalyzer = remember {
        CameraAnalyzer { imageProxy ->
            Log.d("CameraAnalyzer", "Image received: ${state.imageWidth}x${state.imageHeight}")
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                imageSize = Size(image.width, image.height)

                objectDetector.process(image)
                    .addOnSuccessListener { detectedObjects: MutableList<DetectedObject> ->
                        detectedObjects.firstOrNull()?.let { detectedObject ->
                            detectedObject.let {
                                boundingBox = detectedObject.boundingBox.toComposeRect()

                                // Essas variaveis seram atribuidas no inicio sendo ajustadas para a escala da tela
                                //--coordinateX = detectedObject.boundingBox.left.dp
                                //--coordinateY = detectedObject.boundingBox.top.dp

                                val labels = detectedObject.labels.map { it.text }.toString()

                                val label = detectedObject.labels.firstOrNull()?.text.toString()
                                //Busca o nome do produto detectado na base
                                val product = ProductSample.findProductByName(label)

                                viewModel.setTextMessage("${product.name} - ${product.price}")
                            }

                            imageProxy.close()
                        } ?: run {
                            imageProxy.close()
                        }
                    }
                // Pass image to an ML Kit Vision API
                // ...
            }


        }
    }

    // 1 Camera Controller
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                cameraAnalyzer
            )
        }
    }

    // 2 Camera Preview
    CameraPreview(cameraController = cameraController)

    // 3 Overlay
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.2f))
    ) {
        screenSize = Size(maxWidth.dpToPx().toInt(), maxHeight.dpToPx().toInt())

        ObjectOverlay(
            boundsObject = boundingBox,
            nameObject = state.textMessage.toString(),
            coordinateX = coordinateX,
            coordinateY = coordinateY
        )
        /*Text(
            text = state.textMessage ?: "Nenhum produto detectado",
            fontSize = 20.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        )*/

        Log.d("CameraScreen", "Screen size: ${maxWidth.dpToPx()} x ${maxHeight.dpToPx()}")
    }
}


@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraController: LifecycleCameraController,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                this.controller = cameraController
                cameraController.bindToLifecycle(lifecycleOwner)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun ObjectOverlay(
    boundsObject: Rect,
    nameObject: String,
    coordinateX: Dp,
    coordinateY: Dp
) {
    Column(
        modifier = Modifier
            .offset(x = coordinateX, y = coordinateY)
            .size(boundsObject.width.dp, boundsObject.height.dp + boundsObject.height.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(boundsObject.width.dp, boundsObject.height.dp)
                .border(width = 5.dp, color = Color.White, shape = RoundedCornerShape(15.dp)),
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (nameObject.isNotEmpty()) {
            Text(
                text = nameObject,
                fontSize = 20.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White, shape = RoundedCornerShape(5.dp)
                    )
                    .padding(8.dp)
            )
        }
    }
}