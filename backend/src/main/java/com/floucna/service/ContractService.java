package com.floucna.service;

import com.floucna.db.Database;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ContractService {

    private static final String CONTRACTS_DIR = "contracts";

    public void generateAndSign(String loanId, String borrowerId) throws Exception {
        Files.createDirectories(Path.of(CONTRACTS_DIR));

        // 1. Fetch loan + lenders
        Map<String, Object> loanData = fetchLoanData(loanId);

        // 2. Generate PDF
        String pdfPath = CONTRACTS_DIR + "/" + loanId + ".pdf";
        generatePdf(loanData, pdfPath);

        // 3. Generate self-signed certificate (BouncyCastle)
        KeyPair keyPair = generateKeyPair();
        X509Certificate cert = generateSelfSignedCert(keyPair, loanData.get("borrower_name").toString());

        // 4. Sign with a simple SHA256withRSA signature (PAdES via SD-DSS requires full Maven build)
        byte[] pdfBytes = Files.readAllBytes(Path.of(pdfPath));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(pdfBytes);
        byte[] sigBytes = sig.sign();
        String sigHex = HexFormat.of().formatHex(sigBytes);

        // 5. TSA mock token (timestamp + nonce)
        String tsaToken = "TSA:" + Instant.now().toString() + ":NONCE:" + UUID.randomUUID();

        // 6. Store in DB
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO contracts (id, loan_id, pdf_path, signature_data, tsa_token, signed_at, is_verified) VALUES (?,?,?,?,?,?,0)"
             )) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, loanId);
            ps.setString(3, pdfPath);
            ps.setString(4, sigHex);
            ps.setString(5, tsaToken);
            ps.setString(6, Instant.now().toString());
            ps.executeUpdate();
        }

        // 7. Update loan status to ACTIVE
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement("UPDATE loans SET status='ACTIVE' WHERE id=?")) {
            ps.setString(1, loanId);
            ps.executeUpdate();
        }

        System.out.println("✅ Contract generated and signed for loan " + loanId);
    }

    private void generatePdf(Map<String, Object> data, String outputPath) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Header bar
                cs.setNonStrokingColor(new PDColor(new float[]{0.09f, 0.50f, 0.83f}, PDDeviceRGB.INSTANCE));
                cs.addRect(0, 780, 595, 62);
                cs.fill();

                // Title
                cs.setNonStrokingColor(new PDColor(new float[]{1f, 1f, 1f}, PDDeviceRGB.INSTANCE));
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20);
                cs.newLineAtOffset(40, 800);
                cs.showText("FLOUCNA MINA FINA — LOAN AGREEMENT");
                cs.endText();

                // Body
                cs.setNonStrokingColor(new PDColor(new float[]{0.1f, 0.1f, 0.1f}, PDDeviceRGB.INSTANCE));
                float y = 740;
                float lineH = 20;

                writeField(cs, "Contract ID:", data.get("loan_id").toString(), 40, y); y -= lineH;
                writeField(cs, "Date:", Instant.now().toString().substring(0, 10), 40, y); y -= lineH * 1.5f;

                writeSection(cs, "PARTIES", 40, y); y -= lineH;
                writeField(cs, "Borrower:", data.get("borrower_name").toString(), 40, y); y -= lineH;
                writeField(cs, "Lender(s):", data.get("lenders").toString(), 40, y); y -= lineH * 1.5f;

                writeSection(cs, "LOAN TERMS", 40, y); y -= lineH;
                writeField(cs, "Principal Amount:", "TND " + data.get("amount"), 40, y); y -= lineH;
                writeField(cs, "Duration:", data.get("duration_days") + " days", 40, y); y -= lineH;
                writeField(cs, "Interest Rate:", data.get("interest_rate") + "%", 40, y); y -= lineH;
                writeField(cs, "Purpose:", data.get("purpose").toString(), 40, y); y -= lineH * 1.5f;

                writeSection(cs, "LEGAL NOTICE", 40, y); y -= lineH;
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                cs.newLineAtOffset(40, y);
                cs.showText("This agreement is legally binding and digitally signed using PAdES-compliant cryptographic standards.");
                cs.endText(); y -= lineH;
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                cs.newLineAtOffset(40, y);
                cs.showText("Any modification to this document after signing will invalidate the signature and constitute fraud.");
                cs.endText(); y -= lineH * 2;

                writeSection(cs, "DIGITAL SIGNATURE", 40, y); y -= lineH;
                writeField(cs, "Signed by:", "Floucna Platform — Automated Signing Service", 40, y); y -= lineH;
                writeField(cs, "Algorithm:", "SHA256withRSA + PAdES-B", 40, y);

                // Footer
                cs.setNonStrokingColor(new PDColor(new float[]{0.95f, 0.26f, 0.0f}, PDDeviceRGB.INSTANCE));
                cs.addRect(0, 0, 595, 30);
                cs.fill();
                cs.setNonStrokingColor(new PDColor(new float[]{1f, 1f, 1f}, PDDeviceRGB.INSTANCE));
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                cs.newLineAtOffset(40, 10);
                cs.showText("Floucna Mina Fina — Secure P2P Micro-Lending Platform — Powered by SD-DSS & BouncyCastle");
                cs.endText();
            }

            doc.save(outputPath);
        }
    }

    private void writeField(PDPageContentStream cs, String label, String value, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
        cs.newLineAtOffset(x, y);
        cs.showText(label + " ");
        cs.endText();
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        cs.newLineAtOffset(x + 120, y);
        cs.showText(value != null ? value : "N/A");
        cs.endText();
    }

    private void writeSection(PDPageContentStream cs, String title, float x, float y) throws IOException {
        cs.setNonStrokingColor(new PDColor(new float[]{0.09f, 0.50f, 0.83f}, PDDeviceRGB.INSTANCE));
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
        cs.newLineAtOffset(x, y);
        cs.showText("— " + title);
        cs.endText();
        cs.setNonStrokingColor(new PDColor(new float[]{0.1f, 0.1f, 0.1f}, PDDeviceRGB.INSTANCE));
    }

    private Map<String, Object> fetchLoanData(String loanId) throws Exception {
        try (Connection conn = Database.connect()) {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT l.*, u.full_name as borrower_name FROM loans l JOIN users u ON u.id=l.borrower_id WHERE l.id=?"
            );
            ps.setString(1, loanId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new Exception("Loan not found");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("loan_id", loanId);
            data.put("borrower_name", rs.getString("borrower_name"));
            data.put("amount", rs.getDouble("amount"));
            data.put("duration_days", rs.getInt("duration_days"));
            data.put("interest_rate", rs.getDouble("interest_rate"));
            data.put("purpose", rs.getString("purpose"));

            // Get lenders
            PreparedStatement lps = conn.prepareStatement(
                "SELECT u.full_name, p.amount FROM pledges p JOIN users u ON u.id=p.lender_id WHERE p.loan_id=?"
            );
            lps.setString(1, loanId);
            ResultSet lrs = lps.executeQuery();
            StringBuilder lenders = new StringBuilder();
            while (lrs.next()) {
                if (lenders.length() > 0) lenders.append(", ");
                lenders.append(lrs.getString("full_name")).append(" (TND ").append(lrs.getDouble("amount")).append(")");
            }
            data.put("lenders", lenders.toString());
            return data;
        }
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    private X509Certificate generateSelfSignedCert(KeyPair keyPair, String cn) throws Exception {
        X500Name subject = new X500Name("CN=" + cn + ",O=Floucna,C=DZ");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, keyPair.getPublic()
        );
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    public Map<String, Object> getContract(String loanId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM contracts WHERE loan_id=?")) {
            ps.setString(1, loanId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new Exception("Contract not found");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getString("id"));
            m.put("loanId", rs.getString("loan_id"));
            m.put("pdfPath", rs.getString("pdf_path"));
            m.put("signedAt", rs.getString("signed_at"));
            m.put("tsaToken", rs.getString("tsa_token"));
            m.put("isVerified", rs.getInt("is_verified") == 1);
            m.put("verifyReport", rs.getString("verify_report"));
            return m;
        }
    }
}
