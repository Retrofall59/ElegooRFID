package com.tomyn.elegoorfid

import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException

/**
 * Impression directe de la planche d'etiquettes via la fenetre d'impression Android (ajoute le
 * 09/10/2026 a la demande de Tomyn : generer le PDF puis aller le choisir dans une appli
 * d'impression a part etait jugee trop lourde - "ouvre la fenetre d'impression Android pour
 * imprimer directement sur une imprimante Wi-Fi").
 *
 * Passe ce PrintDocumentAdapter a PrintManager.print() (voir MainActivity.imprimerPlanche) : le
 * systeme Android affiche alors sa propre fenetre d'impression, qui liste automatiquement toutes
 * les imprimantes Wi-Fi/reseau deja configurees sur le telephone (plugin "Service d'impression"
 * du fabricant, ou Mopria/Impression par defaut Android) - cette appli n'a besoin de rien savoir
 * sur le reseau ni sur le modele d'imprimante, c'est le travail du framework d'impression.
 *
 * Reutilise exactement PlancheEtiquettes.genererPdf() : meme mise en page, meme grille 3x8, que
 * ce soit pour "Generer le PDF" (export vers un fichier) ou "Imprimer" (ce fichier) - un seul
 * endroit a ajuster si Tomyn passe un jour a une planche de references precises.
 */
class ImpressionPlanche(private val etiquettes: List<PlancheEtiquettes.Etiquette>) : PrintDocumentAdapter() {

    private var document: PdfDocument? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder("etiquettes_elegoo.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PlancheEtiquettes.nombrePages(etiquettes))
            .build()
        // "true" (mise en page toujours consideree comme changee) : plus simple et sans risque -
        // ca force juste une regeneration du PDF dans onWrite a chaque fois, ce qui est de toute
        // facon ce qu'on veut (coherent avec exporterPlanchePdf qui regenere aussi a chaque export).
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: FileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
        }
        try {
            document = PlancheEtiquettes.genererPdf(etiquettes)
            FileOutputStream(destination).use { flux ->
                document?.writeTo(flux)
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback?.onWriteFailed(e.message)
        } finally {
            document?.close()
            document = null
        }
    }

    override fun onFinish() {
        document?.close()
        document = null
    }
}
