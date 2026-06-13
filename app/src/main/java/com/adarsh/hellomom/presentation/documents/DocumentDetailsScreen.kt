package com.adarsh.hellomom.presentation.documents

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.adarsh.hellomom.core.constants.DocumentConstants
import com.adarsh.hellomom.presentation.components.ListShimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailsScreen(
    navController: NavController,
    name: String,
    fileType: String,
    url: String
) {
    val context = LocalContext.current
    val type = DocumentConstants.typeOf(fileType)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { openExternally(context, url, fileType) }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open externally")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (type) {
                DocumentConstants.DocType.IMAGE -> ZoomableImage(url)
                DocumentConstants.DocType.PDF -> PdfPreview(url, context)
                else -> ExternalOnlyPreview(name = name, url = url, fileType = fileType)
            }
        }
    }
}

@Composable
private fun ZoomableImage(url: String) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += panChange
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .transformable(transformState),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

private sealed interface PdfResult {
    data object Loading : PdfResult
    data class Success(val pages: List<Bitmap>) : PdfResult
    data class Error(val message: String) : PdfResult
}

@Composable
private fun PdfPreview(url: String, context: Context) {
    val result by produceState<PdfResult>(initialValue = PdfResult.Loading, url) {
        value = withContext(Dispatchers.IO) { renderPdf(context, url) }
    }

    when (val r = result) {
        is PdfResult.Loading -> ListShimmer()
        is PdfResult.Error -> ExternalOnlyPreview(
            name = "PDF",
            url = url,
            fileType = "pdf",
            message = r.message
        )
        is PdfResult.Success -> LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFF424242)),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(r.pages) { page ->
                Image(
                    bitmap = page.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ExternalOnlyPreview(name: String, url: String, fileType: String, message: String? = null) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (message != null) Icons.Default.ErrorOutline else Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message ?: "Preview isn't available for .$fileType files in-app.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { openExternally(context, url, fileType) }) {
                Icon(Icons.Default.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open with another app")
            }
        }
    }
}

private fun renderPdf(context: Context, url: String): PdfResult {
    return try {
        val file = File(context.cacheDir, "doc_preview_${url.hashCode()}.pdf")
        if (!file.exists() || file.length() == 0L) {
            URL(url).openStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        val pages = ArrayList<Bitmap>(renderer.pageCount)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val width = 1080
            val height = (width.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            pages.add(bitmap)
        }
        renderer.close()
        descriptor.close()
        if (pages.isEmpty()) PdfResult.Error("This PDF has no pages.") else PdfResult.Success(pages)
    } catch (e: Exception) {
        PdfResult.Error("Could not open this PDF.")
    }
}

private fun openExternally(context: Context, url: String, fileType: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), DocumentConstants.mimeFor(fileType))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
