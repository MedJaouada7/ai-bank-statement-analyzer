package com.medj.ai_bank_statement_analyzer.spikes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/* PDF text-position spike : print what PDFBox extracts so we can judge whether row reconstruction is feasible */
public class PdfTextPositionSpike extends PDFTextStripper {

    record Word(int page, float x, float xEnd, float y, String text) {}

    private final List<Word> words = new ArrayList<>();

    PdfTextPositionSpike() throws IOException {
        setSortByPosition(true);
    }

    // Override the writeString method to capture text positions
    @Override
    protected void writeString(String text, List<TextPosition> positions) throws IOException {
        StringBuilder word = new StringBuilder();
        float x0 = 0;
        float x1 = 0;
        float y = 0;
        for (TextPosition p : positions) {
            String u = p.getUnicode();
            if (u == null || u.isBlank()) {
                if (word.length() > 0) {
                    words.add(new Word(getCurrentPageNo(), x0, x1, y, word.toString()));
                    word.setLength(0);
                }
                continue;
            }
            if (word.length() == 0) {
                x0 = p.getXDirAdj();
                y = p.getYDirAdj();
            }
            word.append(u);
            x1 = p.getXDirAdj() + p.getWidthDirAdj();
        }
        if (word.length() > 0) {
            words.add(new Word(getCurrentPageNo(), x0, x1, y, word.toString()));
        }
    }

    // Print the captured words in a structured format
    void printRows() {
        words.sort(
                Comparator.comparingInt(Word::page).thenComparing(Word::y).thenComparing(Word::x));
        int page = -1;
        float rowY = -100;
        StringBuilder row = new StringBuilder();
        for (Word w : words) {
            boolean newRow = w.page() != page || Math.abs(w.y() - rowY) > 2f;
            if (newRow) {
                if (row.length() > 0) {
                    System.out.println(row);
                }
                row.setLength(0);
                row.append(String.format("p%d y=%6.1f |", w.page(), w.y()));
                page = w.page();
                rowY = w.y();
            }
            row.append(String.format(" [%.0f..%.0f]%s", w.x(), w.xEnd(), w.text()));
        }
        System.out.println(row);
    }

    public static void main(String[] args) throws IOException {
        File pdf =
                new File(
                        args.length > 0
                                ? args[0]
                                : "tools/synthetic_statements/out/statement_layout_a_2025-10.pdf");
        long start = System.nanoTime();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PdfTextPositionSpike stripper = new PdfTextPositionSpike();
            stripper.getText(document);
            stripper.printRows();
        }
        System.out.printf("extraction took %d ms%n", (System.nanoTime() - start) / 1_000_000);
    }
}
