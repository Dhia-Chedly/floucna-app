package com.floucna.service;

import com.floucna.db.Database;
import com.floucna.util.ApiException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class ContractService {

    private static final Path CONTRACT_ROOT = Path.of("storage", "contracts").toAbsolutePath().normalize();

    public Map<String, Object> generatePdfContract(String loanId) throws Exception {
        LoanContext loan = loadLoanContext(loanId);
        if (!"APPROVED".equals(loan.status())) {
            throw new ApiException(400, "Loan must be APPROVED before generating a contract");
        }

        Path loanDir = CONTRACT_ROOT.resolve(loanId).normalize();
        if (!loanDir.startsWith(CONTRACT_ROOT)) {
            throw new ApiException(400, "Invalid contract path");
        }
        Files.createDirectories(loanDir);

        Path unsignedPdf = loanDir.resolve("loan_agreement_unsigned.pdf");
        generatePdf(loan, unsignedPdf);

        upsertContract(loanId, Map.of(
            "unsigned_pdf_path", unsignedPdf.toString(),
            "status", "PDF_GENERATED"
        ));

        return getContractByLoanId(loanId);
    }

    public Map<String, Object> signPdfContract(String loanId) throws Exception {
        ContractRow contract = loadContract(loanId);
        if (contract.unsignedPdfPath() == null || contract.unsignedPdfPath().isBlank()) {
            throw new ApiException(400, "Unsigned PDF not found. Generate PDF first.");
        }

        Path unsignedPath = Path.of(contract.unsignedPdfPath());
        if (!Files.exists(unsignedPath)) {
            throw new ApiException(404, "Unsigned PDF file is missing");
        }

        byte[] pdfBytes = Files.readAllBytes(unsignedPath);

        KeyPair keyPair = generateKeyPair();
        X509Certificate cert = generateSelfSignedCert(keyPair, "Floucna-Signing");

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(pdfBytes);
        byte[] signatureBytes = signature.sign();

        // Persist as separate signed file (demo-style signature process).
        Path signedPath = unsignedPath.getParent().resolve("loan_agreement_signed.pdf");
        Files.copy(unsignedPath, signedPath, StandardCopyOption.REPLACE_EXISTING);

        String pdfHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdfBytes));

        upsertContract(loanId, Map.of(
            "signed_pdf_path", signedPath.toString(),
            "pdf_hash", pdfHash,
            "signature_level", "PAdES-B (demo)",
            "status", "SIGNED",
            "signed_at", Instant.now().toString()
        ));

        return getContractByLoanId(loanId);
    }

    public Map<String, Object> timestampContract(String loanId) throws Exception {
        ContractRow contract = loadContract(loanId);
        if (contract.signedPdfPath() == null || contract.signedPdfPath().isBlank()) {
            throw new ApiException(400, "Signed PDF not found. Sign PDF first.");
        }

        Path signedPath = Path.of(contract.signedPdfPath());
        if (!Files.exists(signedPath)) {
            throw new ApiException(404, "Signed PDF file is missing");
        }

        Path timestampedPath = signedPath.getParent().resolve("loan_agreement_timestamped.pdf");
        Files.copy(signedPath, timestampedPath, StandardCopyOption.REPLACE_EXISTING);

        upsertContract(loanId, Map.of(
            "timestamped_pdf_path", timestampedPath.toString(),
            "status", "TIMESTAMPED",
            "timestamped_at", Instant.now().toString()
        ));

        return getContractByLoanId(loanId);
    }

    public String getDownloadPathForViewer(String loanId, String viewerUserId, String viewerRole) throws Exception {
        ContractRow contract = loadContractWithLoan(loanId);
        boolean isAdmin = "ADMIN".equals(viewerRole);
        boolean isOwner = contract.borrowerId() != null && contract.borrowerId().equals(viewerUserId);
        if (!(isAdmin || isOwner)) {
            throw new ApiException(403, "Forbidden");
        }

        if (contract.timestampedPdfPath() != null && !contract.timestampedPdfPath().isBlank()) {
            return contract.timestampedPdfPath();
        }
        if (contract.signedPdfPath() != null && !contract.signedPdfPath().isBlank()) {
            return contract.signedPdfPath();
        }
        if (contract.unsignedPdfPath() != null && !contract.unsignedPdfPath().isBlank()) {
            return contract.unsignedPdfPath();
        }
        throw new ApiException(404, "Contract file is not available");
    }

    public Map<String, Object> getContractForViewer(String loanId, String viewerUserId, String viewerRole) throws Exception {
        ContractRow contract = loadContractWithLoan(loanId);
        boolean isAdmin = "ADMIN".equals(viewerRole);
        boolean isOwner = contract.borrowerId() != null && contract.borrowerId().equals(viewerUserId);
        if (!(isAdmin || isOwner)) {
            throw new ApiException(403, "Forbidden");
        }
        return rowToMap(contract);
    }

    public List<Map<String, Object>> listContractsForAdmin() throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT c.*, lr.borrower_id, u.full_name AS borrower_name FROM contracts c " +
                "JOIN loan_requests lr ON lr.id = c.loan_id " +
                "JOIN users u ON u.id = lr.borrower_id ORDER BY c.created_at DESC"
             )) {
            ResultSet rs = ps.executeQuery();
            List<Map<String, Object>> out = new ArrayList<>();
            while (rs.next()) {
                ContractRow row = new ContractRow(
                    rs.getString("id"),
                    rs.getString("loan_id"),
                    rs.getString("borrower_id"),
                    rs.getString("unsigned_pdf_path"),
                    rs.getString("signed_pdf_path"),
                    rs.getString("timestamped_pdf_path"),
                    rs.getString("pdf_hash"),
                    rs.getString("signature_level"),
                    rs.getString("status"),
                    rs.getString("verification_result"),
                    rs.getString("verification_report"),
                    rs.getString("signed_at"),
                    rs.getString("timestamped_at"),
                    rs.getString("verified_at"),
                    rs.getString("created_at")
                );
                Map<String, Object> map = rowToMap(row);
                map.put("borrowerName", rs.getString("borrower_name"));
                out.add(map);
            }
            return out;
        }
    }

    public Map<String, Object> getContractByLoanId(String loanId) throws Exception {
        return rowToMap(loadContractWithLoan(loanId));
    }

    private void generatePdf(LoanContext loan, Path output) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 760;
                float line = 18;

                writeLine(cs, "FLOUCNA MINA FINA - LOAN AGREEMENT", 50, y, true, 16); y -= 2 * line;
                writeLine(cs, "Agreement Number: " + loan.loanId(), 50, y, false, 11); y -= line;
                writeLine(cs, "Date: " + Instant.now().toString().substring(0, 10), 50, y, false, 11); y -= line;
                writeLine(cs, "Borrower: " + loan.borrowerName() + " (" + loan.borrowerEmail() + ")", 50, y, false, 11); y -= line;
                writeLine(cs, "Amount: " + loan.amount() + " " + loan.currency(), 50, y, false, 11); y -= line;
                writeLine(cs, "Duration: " + loan.durationDays() + " days", 50, y, false, 11); y -= line;
                writeLine(cs, "Purpose: " + loan.purpose(), 50, y, false, 11); y -= line;
                writeLine(cs, "Demo Score: " + loan.demoScore() + " (" + loan.scoreLabel() + ")", 50, y, false, 11); y -= 2 * line;

                writeLine(cs, "Borrower Obligations:", 50, y, true, 12); y -= line;
                writeLine(cs, "1. Repay the amount according to agreed terms.", 60, y, false, 10); y -= line;
                writeLine(cs, "2. Provide truthful declarations and supporting documentation.", 60, y, false, 10); y -= line;
                writeLine(cs, "3. Respect legal and compliance obligations.", 60, y, false, 10); y -= 2 * line;

                writeLine(cs, "Admin Approval Reference: " + loan.loanId(), 50, y, false, 10); y -= line;
                writeLine(cs, "Signature Section: Generated for digital signing and timestamping.", 50, y, false, 10);
            }

            doc.save(output.toFile());
        }
    }

    private void writeLine(PDPageContentStream cs, String text, float x, float y, boolean bold, int size) throws IOException {
        cs.beginText();
        cs.setFont(new PDType1Font(bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA), size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private LoanContext loadLoanContext(String loanId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT lr.id, lr.borrower_id, lr.amount, lr.currency, lr.duration_days, lr.purpose, lr.demo_score, lr.score_label, lr.status, " +
                 "u.full_name, u.email FROM loan_requests lr JOIN users u ON u.id = lr.borrower_id WHERE lr.id = ?"
             )) {
            ps.setString(1, loanId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new ApiException(404, "Loan request not found");
            }

            return new LoanContext(
                rs.getString("id"),
                rs.getString("borrower_id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getDouble("amount"),
                rs.getString("currency"),
                rs.getInt("duration_days"),
                rs.getString("purpose"),
                rs.getInt("demo_score"),
                rs.getString("score_label"),
                rs.getString("status")
            );
        }
    }

    private ContractRow loadContract(String loanId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM contracts WHERE loan_id=?")) {
            ps.setString(1, loanId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new ApiException(404, "Contract not found. Generate PDF first.");
            }
            return new ContractRow(
                rs.getString("id"),
                rs.getString("loan_id"),
                null,
                rs.getString("unsigned_pdf_path"),
                rs.getString("signed_pdf_path"),
                rs.getString("timestamped_pdf_path"),
                rs.getString("pdf_hash"),
                rs.getString("signature_level"),
                rs.getString("status"),
                rs.getString("verification_result"),
                rs.getString("verification_report"),
                rs.getString("signed_at"),
                rs.getString("timestamped_at"),
                rs.getString("verified_at"),
                rs.getString("created_at")
            );
        }
    }

    private ContractRow loadContractWithLoan(String loanId) throws Exception {
        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT c.*, lr.borrower_id FROM contracts c JOIN loan_requests lr ON lr.id = c.loan_id WHERE c.loan_id=?"
             )) {
            ps.setString(1, loanId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new ApiException(404, "Contract not found");
            }
            return new ContractRow(
                rs.getString("id"),
                rs.getString("loan_id"),
                rs.getString("borrower_id"),
                rs.getString("unsigned_pdf_path"),
                rs.getString("signed_pdf_path"),
                rs.getString("timestamped_pdf_path"),
                rs.getString("pdf_hash"),
                rs.getString("signature_level"),
                rs.getString("status"),
                rs.getString("verification_result"),
                rs.getString("verification_report"),
                rs.getString("signed_at"),
                rs.getString("timestamped_at"),
                rs.getString("verified_at"),
                rs.getString("created_at")
            );
        }
    }

    private void upsertContract(String loanId, Map<String, String> updates) throws Exception {
        try (Connection conn = Database.connect()) {
            PreparedStatement find = conn.prepareStatement("SELECT id FROM contracts WHERE loan_id=?");
            find.setString(1, loanId);
            ResultSet rs = find.executeQuery();

            if (!rs.next()) {
                PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO contracts (id, loan_id, unsigned_pdf_path, signed_pdf_path, timestamped_pdf_path, pdf_hash, signature_level, status, created_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?)"
                );
                ins.setString(1, UUID.randomUUID().toString());
                ins.setString(2, loanId);
                ins.setString(3, updates.get("unsigned_pdf_path"));
                ins.setString(4, updates.get("signed_pdf_path"));
                ins.setString(5, updates.get("timestamped_pdf_path"));
                ins.setString(6, updates.get("pdf_hash"));
                ins.setString(7, updates.get("signature_level"));
                ins.setString(8, updates.getOrDefault("status", "PDF_GENERATED"));
                ins.setString(9, Instant.now().toString());
                ins.executeUpdate();
            } else {
                PreparedStatement upd = conn.prepareStatement(
                    "UPDATE contracts SET unsigned_pdf_path=COALESCE(?, unsigned_pdf_path), " +
                    "signed_pdf_path=COALESCE(?, signed_pdf_path), " +
                    "timestamped_pdf_path=COALESCE(?, timestamped_pdf_path), " +
                    "pdf_hash=COALESCE(?, pdf_hash), signature_level=COALESCE(?, signature_level), " +
                    "status=COALESCE(?, status), signed_at=COALESCE(?, signed_at), timestamped_at=COALESCE(?, timestamped_at) " +
                    "WHERE loan_id=?"
                );
                upd.setString(1, updates.get("unsigned_pdf_path"));
                upd.setString(2, updates.get("signed_pdf_path"));
                upd.setString(3, updates.get("timestamped_pdf_path"));
                upd.setString(4, updates.get("pdf_hash"));
                upd.setString(5, updates.get("signature_level"));
                upd.setString(6, updates.get("status"));
                upd.setString(7, updates.get("signed_at"));
                upd.setString(8, updates.get("timestamped_at"));
                upd.setString(9, loanId);
                upd.executeUpdate();
            }
        }
    }

    private Map<String, Object> rowToMap(ContractRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.id());
        out.put("loanId", row.loanId());
        out.put("unsignedPdfPath", row.unsignedPdfPath());
        out.put("signedPdfPath", row.signedPdfPath());
        out.put("timestampedPdfPath", row.timestampedPdfPath());
        out.put("pdfHash", row.pdfHash());
        out.put("signatureLevel", row.signatureLevel());
        out.put("status", row.status());
        out.put("verificationResult", row.verificationResult());
        out.put("verificationReport", row.verificationReport());
        out.put("signedAt", row.signedAt());
        out.put("timestampedAt", row.timestampedAt());
        out.put("verifiedAt", row.verifiedAt());
        out.put("createdAt", row.createdAt());
        if (row.borrowerId() != null) {
            out.put("borrowerId", row.borrowerId());
        }
        return out;
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    private X509Certificate generateSelfSignedCert(KeyPair keyPair, String commonName) throws Exception {
        X500Name subject = new X500Name("CN=" + commonName + ",O=Floucna,C=TN");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date now = new Date();
        Date expiry = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            subject,
            serial,
            now,
            expiry,
            subject,
            keyPair.getPublic()
        );
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private record LoanContext(
        String loanId,
        String borrowerId,
        String borrowerName,
        String borrowerEmail,
        double amount,
        String currency,
        int durationDays,
        String purpose,
        int demoScore,
        String scoreLabel,
        String status
    ) {}

    private record ContractRow(
        String id,
        String loanId,
        String borrowerId,
        String unsignedPdfPath,
        String signedPdfPath,
        String timestampedPdfPath,
        String pdfHash,
        String signatureLevel,
        String status,
        String verificationResult,
        String verificationReport,
        String signedAt,
        String timestampedAt,
        String verifiedAt,
        String createdAt
    ) {}
}
