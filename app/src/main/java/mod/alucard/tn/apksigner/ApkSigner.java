package mod.alucard.tn.apksigner;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.apksigner.ApkSignerTool;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import kellinwood.security.zipsigner.optional.KeyStoreFileManager;
import mod.jbk.build.BuiltInLibraries;

public class ApkSigner {

    private static final File EXTRACTED_TESTKEY_FILES_DIRECTORY = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "testkey");

    /**
     * Sign an APK with testkey.
     *
     * @param inputPath  The APK file to sign
     * @param outputPath File to output the signed APK to
     * @param callback   Callback for System.out during signing. May be null
     */
    public void signWithTestKey(@NonNull String inputPath, @NonNull String outputPath, @Nullable LogCallback callback) {
        try (LogWriter logger = new LogWriter(callback)) {
            long savedTimeMillis = System.currentTimeMillis();
            PrintStream oldOut = System.out;

            List<String> args = Arrays.asList(
                    "sign",
                    "--in",
                    inputPath,
                    "--out",
                    outputPath,
                    "--key",
                    new File(EXTRACTED_TESTKEY_FILES_DIRECTORY, "testkey.pk8").getAbsolutePath(),
                    "--cert",
                    new File(EXTRACTED_TESTKEY_FILES_DIRECTORY, "testkey.x509.pem").getAbsolutePath()
            );

            logger.write("Signing an APK file with these arguments: " + args);

            /* If the signing has a callback, we need to change System.out to our logger */
            if (callback != null) {
                try (PrintStream stream = new PrintStream(logger)) {
                    System.setOut(stream);
                }
            }

            try {
                ApkSignerTool.main(args.toArray(new String[0]));
            } catch (Exception e) {
                LogCallback.errorCount.incrementAndGet();
                logger.write("An error occurred while trying to sign the APK file " + inputPath +
                        " and outputting it to " + outputPath + ": " + e.getMessage() + "\n" +
                        "Stack trace: " + Log.getStackTraceString(e));
            }

            logger.write("Signing an APK file took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");

            if (callback != null) {
                System.setOut(oldOut);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Signs an APK with a JKS/PKCS12/BKS keystore.
     *
     * <p>Deliberately not going through {@code ApkSignerTool}'s CLI: on Android,
     * {@code KeyStore.getInstance("JKS")} resolves to the BouncyCastle BKS implementation, so a real
     * Java-format JKS cannot be read that way ({@code IOException: Wrong version of key store}); and
     * the CLI calls {@code System.exit()} when it fails, which kills Sketchware itself. The keystore
     * is loaded with the same type-agnostic loader the Export signing uses, and signed through the
     * apksig programmatic API.</p>
     */
    public void signWithKeyStore(@NonNull String inputFilePath, @NonNull String outputFilePath,
                                 @NonNull String keyStorePath, @NonNull String keyStorePassword,
                                 @NonNull String keyStoreKeyAlias, @NonNull String keyPassword, @Nullable LogCallback callback) {
        try (LogWriter logger = new LogWriter(callback)) {
            long savedTimeMillis = System.currentTimeMillis();

            logger.write("Signing the APK " + inputFilePath + " with the keystore " + keyStorePath
                    + " (alias: " + keyStoreKeyAlias + ")\n");

            try {
                KeyStore keyStore = KeyStoreFileManager.loadKeyStore(keyStorePath, keyStorePassword.toCharArray());
                Key key = keyStore.getKey(keyStoreKeyAlias, keyPassword.toCharArray());
                if (!(key instanceof PrivateKey)) {
                    throw new GeneralSecurityException("Alias \"" + keyStoreKeyAlias + "\" does not hold a private key");
                }

                Certificate[] chain = keyStore.getCertificateChain(keyStoreKeyAlias);
                if (chain == null || chain.length == 0) {
                    Certificate certificate = keyStore.getCertificate(keyStoreKeyAlias);
                    chain = certificate == null ? new Certificate[0] : new Certificate[]{certificate};
                }
                List<X509Certificate> certificates = new ArrayList<>(chain.length);
                for (Certificate certificate : chain) {
                    certificates.add((X509Certificate) certificate);
                }

                com.android.apksig.ApkSigner.SignerConfig signerConfig = new com.android.apksig.ApkSigner.SignerConfig.Builder(
                        keyStoreKeyAlias, (PrivateKey) key, certificates).build();

                new com.android.apksig.ApkSigner.Builder(Collections.singletonList(signerConfig))
                        .setInputApk(new File(inputFilePath))
                        .setOutputApk(new File(outputFilePath))
                        .setV1SigningEnabled(true)
                        .setV2SigningEnabled(true)
                        .setV3SigningEnabled(true)
                        .build()
                        .sign();
            } catch (Exception e) {
                LogCallback.errorCount.incrementAndGet();
                logger.write("Failed to sign APK with keystore: " + Log.getStackTraceString(e));
            }

            logger.write("Signing an APK took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public interface LogCallback {
        AtomicInteger errorCount = new AtomicInteger(0);

        void onNewLineLogged(String line);
    }

    private static class LogWriter extends OutputStream {

        private final LogCallback mCallback;
        private String mCache = "";

        private LogWriter(LogCallback callback) {
            mCallback = callback;
        }

        @Override
        public void write(int b) {
            if (isLoggingDisabled()) return;

            mCache += (char) b;

            if (((char) b) == '\n') {
                mCallback.onNewLineLogged(mCache);
                mCache = "";
            }
        }

        private void write(String s) {
            if (isLoggingDisabled()) return;

            for (byte b : s.getBytes()) {
                write(b);
            }
        }

        private boolean isLoggingDisabled() {
            return mCallback == null;
        }
    }
}
