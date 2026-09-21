package pro.sketchware.utility;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class BinaryExecutor {

    private final ProcessBuilder mProcess = new ProcessBuilder();
    private final StringWriter mWriter = new StringWriter();

    public void setCommands(ArrayList<String> arrayList) {
        mProcess.command(arrayList);
    }

    public String execute() {
        try {
            runProcess();
        } catch (IOException e) {
            // Some OEMs may clear executable bits in app storage. Repair and retry once.
            if (isPermissionDenied(e) && tryRecoverExecutablePath()) {
                try {
                    runProcess();
                } catch (Exception retryException) {
                    retryException.printStackTrace(new PrintWriter(mWriter));
                }
            } else {
                e.printStackTrace(new PrintWriter(mWriter));
            }
        } catch (Exception e) {
            e.printStackTrace(new PrintWriter(mWriter));
        }
        return mWriter.toString();
    }

    private void runProcess() throws IOException {
        Scanner scanner = new Scanner(mProcess.start().getErrorStream());
        while (scanner.hasNextLine()) {
            mWriter.append(scanner.nextLine());
            mWriter.append(System.lineSeparator());
        }
    }

    private boolean isPermissionDenied(IOException e) {
        String message = e.getMessage();
        return message != null && message.contains("Permission denied");
    }

    private boolean tryRecoverExecutablePath() {
        List<String> command = mProcess.command();
        if (command == null || command.isEmpty()) {
            return false;
        }

        File executable = new File(command.get(0));
        if (tryFixExecutablePermission(executable)) {
            return true;
        }

        String executablePath = executable.getAbsolutePath();
        if (!executablePath.contains("/cache/")) {
            return false;
        }

        File filesExecutable = new File(executablePath.replace("/cache/", "/files/"));
        if (!filesExecutable.exists()) {
            if (!executable.exists() || !copyFile(executable, filesExecutable)) {
                return false;
            }
        }

        if (!tryFixExecutablePermission(filesExecutable)) {
            return false;
        }

        command.set(0, filesExecutable.getAbsolutePath());
        mProcess.command(command);
        mWriter.append("Retried with executable moved to files dir: ")
                .append(filesExecutable.getAbsolutePath())
                .append(System.lineSeparator());
        return true;
    }

    private boolean tryFixExecutablePermission(File executable) {
        if (!executable.exists()) {
            return false;
        }

        executable.setReadable(true, true);
        executable.setWritable(true, true);
        executable.setExecutable(true, true);
        return executable.canExecute();
    }

    private boolean copyFile(File source, File destination) {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        try (InputStream inputStream = new FileInputStream(source);
             OutputStream outputStream = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public String getLog() {
        return mWriter.toString();
    }
}