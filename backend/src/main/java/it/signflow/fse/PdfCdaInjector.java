package it.signflow.fse;

/** Embeds an already generated CDA R2 as the cda.xml attachment required by the national FSE Gateway. */
public interface PdfCdaInjector {
    byte[] inject(byte[] pdf, byte[] cdaXml);
}
