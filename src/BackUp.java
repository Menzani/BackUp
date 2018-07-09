package it.menzani.backup;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.jcraft.jsch.*;
import it.menzani.backup.compress.TarGzFile;
import org.apache.commons.compress.utils.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;

public class BackUp implements SftpProgressMonitor {
    public static void main(String[] args) throws Exception {
        Configuration configuration = loadConfiguration();
        if (configuration == null) {
            System.out.println("Saved default configuration file");
            return;
        }
        ZoneId timeZone = ZoneId.of(configuration.getTimeZone());
        Path localBackupFolder = Paths.get(configuration.getBackup().getLocalBackupFolder());
        Path serverFolder = Paths.get(configuration.getBackup().getServerFolder());
        Configuration.Upload upload = configuration.getUpload();
        Configuration.Host host = null;
        if (upload != null) {
            host = upload.getHost();
        }

        LocalDate now = LocalDate.now(timeZone);
        BackupName backupName = new BackupName(now);
        BackupName oldBackupName = null;
        int deleteOldBackups = configuration.getDeleteOldBackups();
        if (deleteOldBackups > 0) {
            oldBackupName = new BackupName(now.minusDays(deleteOldBackups));
        }

        System.out.println("Creazione backup del " + backupName);
        Path backup = localBackupFolder.resolve(backupName.getFileName());
        try (TarGzFile archive = new TarGzFile(backup)) {
            archive.bundleFile(serverFolder.resolve("banned-ips.json"));
            archive.bundleFile(serverFolder.resolve("banned-players.json"));
            archive.bundleFile(serverFolder.resolve("ops.json"));
            archive.bundleFile(serverFolder.resolve("whitelist.json"));
            System.out.println("Backup di Overworld");
            archive.bundleDirectory(serverFolder.resolve("world"));
            System.out.println("Backup di Nether");
            archive.bundleDirectory(serverFolder.resolve("world_nether"));
            System.out.println("Backup di The End");
            archive.bundleDirectory(serverFolder.resolve("world_the_end"));
        }

        if (upload != null) {
            System.out.println("Connessione a " + host.getName());
            JSch ssh = new JSch();
            ssh.addIdentity(host.getPrivateKeyFile());
            ssh.setKnownHosts(upload.getKnownHostsFile());
            Session session = ssh.getSession(host.getUser(), host.getName());
            session.connect();
            try {
                ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect();
                try {
                    long backupSize = Files.size(backup);
                    String remoteBackupFolder = upload.getRemoteBackupFolder();
                    channel.put(Files.newInputStream(backup), remoteBackupFolder + backupName.getFileName(), new BackUp(backupSize));

                    if (oldBackupName != null) {
                        logBackupDeletion(oldBackupName);
                        try {
                            channel.rm(remoteBackupFolder + oldBackupName.getFileName());
                        } catch (SftpException e) {
                            if (e.id != 2) throw e;
                        }
                    }
                } finally {
                    channel.disconnect();
                }
            } finally {
                session.disconnect();
            }
        }

        if (upload != null && configuration.getDeleteCache()) {
            System.out.println("Eliminazione cache");
            Files.delete(backup);
        } else if (oldBackupName != null) {
            if (upload == null) {
                logBackupDeletion(oldBackupName);
            }
            Files.deleteIfExists(localBackupFolder.resolve(oldBackupName.getFileName()));
        }
    }

    private static Configuration loadConfiguration() throws IOException {
        Path config = Paths.get("config.yml");
        if (Files.notExists(config)) {
            try (InputStream in = Configuration.class.getResourceAsStream("config.yml")) {
                try (OutputStream out = Files.newOutputStream(config)) {
                    IOUtils.copy(in, out);
                }
            }
            return null;
        }
        YAMLFactory factory = new YAMLFactory();
        factory.enable(JsonParser.Feature.ALLOW_YAML_COMMENTS);
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NON_PRIVATE);
        mapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NON_PRIVATE);
        return mapper.readValue(Files.newBufferedReader(config), Configuration.class);
    }

    private static void logBackupDeletion(BackupName backupName) {
        System.out.println("Eliminazione backup del " + backupName);
    }

    private final long total;
    private double sent;
    private short lastPercent;

    private BackUp(long total) {
        this.total = total;
    }

    @Override
    public void init(int op, String source, String destination, long max) {
        System.out.println("Upload di " + destination + " (" + formatByteAmount(total) + ')');
    }

    // https://stackoverflow.com/a/3758880/3453226
    private static String formatByteAmount(long amount) {
        final int unit = 1024;
        if (amount < unit) return amount + " B";
        int exp = (int) (Math.log(amount) / Math.log(unit));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", amount / Math.pow(unit, exp), pre);
    }

    @Override
    public boolean count(long bytes) {
        sent += bytes;

        double progress = sent / total;
        short percent = (short) Math.round(progress * 100);
        if (percent != lastPercent && percent % 5 == 0) {
            System.out.println("   " + percent + '%');
            lastPercent = percent;
        }

        return true;
    }

    @Override
    public void end() {
    }
}
