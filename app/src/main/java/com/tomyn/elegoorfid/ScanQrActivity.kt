package com.tomyn.elegoorfid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Scan du QR des etiquettes directement depuis l'appareil photo de l'appli (ajoute en v0.29 a la
 * demande de Tomyn) : jusque-la, le lien "elegoorfid://dump/<hex>" encode dans le QR (v0.21, voir
 * PlancheEtiquettes.lienQrPourDump) ne pouvait etre ouvert que via une appli de scan QR externe
 * (ou le detecteur integre a l'appareil photo de certains telephones). CameraX pour la previsualisation
 * et l'analyse d'image, ZXing pour le decodage - ZXing est deja une dependance du projet depuis la
 * v0.18, mais uniquement pour l'ENCODAGE du QR sur les etiquettes (QRCodeWriter) ; cet ecran
 * reutilise la meme bibliotheque pour le DECODAGE, aucune dependance supplementaire necessaire
 * pour cette partie.
 *
 * MISE EN GARDE (honnetete "cash et franc", comme pour les precedents bugs de compilation reels) :
 * CameraX est une API nettement plus large et plus complexe que tout ce qui a ete stubbe jusqu'ici
 * dans ce projet (ProcessCameraProvider, ImageAnalysis.Analyzer, ImageProxy, les generiques de
 * ListenableFuture...). La verification par compilation contre des stubs ecrits a la main a deja
 * rate trois vraies erreurs de compilation cette session (ParcelFileDescriptor, EditText.text,
 * AlertDialog setMessage+setItems) precisement quand le stub et le code reel partageaient la meme
 * hypothese fausse sur une API Android - le risque que ca se reproduise ici est plus eleve que
 * d'habitude, vu la taille de la surface stubbee d'un coup. A tester en priorite si la compilation
 * GitHub Actions echoue sur ce fichier specifiquement.
 */
class ScanQrActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var executeurAnalyse: ExecutorService
    private val lecteurQr = MultiFormatReader()
    private var dejaTraite = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_qr)
        previewView = findViewById(R.id.previewViewScanQr)
        findViewById<ImageButton>(R.id.btnFermerScanQr).setOnClickListener { finish() }
        executeurAnalyse = Executors.newSingleThreadExecutor()

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            demarrerCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CODE_PERMISSION_CAMERA)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CODE_PERMISSION_CAMERA) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            demarrerCamera()
        } else {
            Toast.makeText(this, "Permission caméra refusée, impossible de scanner un QR.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun demarrerCamera() {
        val fournisseurFutur = ProcessCameraProvider.getInstance(this)
        fournisseurFutur.addListener({
            try {
                val fournisseur = fournisseurFutur.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analyse = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analyse.setAnalyzer(executeurAnalyse) { image -> analyserImage(image) }
                fournisseur.unbindAll()
                fournisseur.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyse)
            } catch (e: Exception) {
                Toast.makeText(this, "Impossible de démarrer la caméra : ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Decode une image de la camera (format YUV_420_888 impose par CameraX pour ImageAnalysis) -
     * seul le premier plan (Y, la luminance) sert a ZXing, pas besoin de la couleur pour lire un
     * QR. rowStride plutot que "width" pour dataWidth : une ligne de l'image peut etre alignee en
     * memoire avec un peu de remplissage selon le capteur - rowStride est la vraie largeur en
     * memoire, "width" seul donnerait une image decalee et illisible sur certains telephones.
     */
    private fun analyserImage(image: ImageProxy) {
        if (dejaTraite) {
            image.close()
            return
        }
        try {
            val plan = image.planes[0]
            val tampon = plan.buffer
            val donnees = ByteArray(tampon.remaining())
            tampon.get(donnees)
            val source = PlanarYUVLuminanceSource(
                donnees, plan.rowStride, image.height,
                0, 0, image.width, image.height, false
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            val resultat = try {
                lecteurQr.decode(bitmap)
            } catch (e: NotFoundException) {
                null
            }
            if (resultat != null) {
                dejaTraite = true
                runOnUiThread { validerResultat(resultat.text) }
            }
        } catch (e: Exception) {
            // Image illisible sur ce cliche (flou, mauvais cadrage...) - pas grave, le flux
            // continue et la prochaine image sera retentee automatiquement.
        } finally {
            image.close()
        }
    }

    private fun validerResultat(texte: String) {
        val resultat = Intent()
        resultat.putExtra(EXTRA_LIEN, texte)
        setResult(Activity.RESULT_OK, resultat)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        executeurAnalyse.shutdown()
    }

    companion object {
        private const val CODE_PERMISSION_CAMERA = 501
        const val EXTRA_LIEN = "lien_qr"
    }
}
