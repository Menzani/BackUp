package it.menzani.backup;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpProgressMonitor;
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
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

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
        Configuration.Host host = configuration.getUpload().getHost();

        LocalDate now = LocalDate.now(timeZone);
        String name = now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG));
        String fileName = name + ".tar.gz";
        Path backup = localBackupFolder.resolve(fileName);

        System.out.println("Creazione backup del " + name);
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

        System.out.println("Connessione a " + host.getName());
        JSch ssh = new JSch();
        ssh.addIdentity(host.getPrivateKeyFile());
        ssh.setKnownHosts(configuration.getUpload().getKnownHostsFile());
        Session session = ssh.getSession(host.getUser(), host.getName());
        session.connect();
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();
        long backupSize = Files.size(backup);
        channel.put(Files.newInputStream(backup), configuration.getUpload().getRemoteBackupFolder() + fileName, new BackUp(backupSize));
        channel.disconnect();
        session.disconnect();

        if (configuration.getDeleteCache()) {
            System.out.println("Eliminazione cache");
            Files.delete(backup);
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

    private static String formatByteAmount(long amount) {
        final int unit = 1000;
        if (amount < unit) return amount + " B";
        int exp = (int) (Math.log(amount) / Math.log(unit));
        char pre = "kMGTPE".charAt(exp - 1);
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
