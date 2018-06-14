package it.menzani.backup;

class Configuration {
    private Backup backup;
    private Upload upload;
    private String timeZone;
    private boolean deleteCache;
    private int deleteOldBackups;

    Backup getBackup() {
        return backup;
    }

    Upload getUpload() {
        return upload;
    }

    String getTimeZone() {
        return timeZone;
    }

    boolean getDeleteCache() {
        return deleteCache;
    }

    int getDeleteOldBackups() {
        return deleteOldBackups;
    }

    static class Backup {
        private String serverFolder;
        private String localBackupFolder;

        String getServerFolder() {
            return serverFolder;
        }

        String getLocalBackupFolder() {
            return localBackupFolder;
        }
    }

    static class Upload {
        private Host host;
        private String knownHostsFile;
        private String remoteBackupFolder;

        Host getHost() {
            return host;
        }

        String getKnownHostsFile() {
            return knownHostsFile;
        }

        String getRemoteBackupFolder() {
            return remoteBackupFolder;
        }
    }

    static class Host {
        private String name;
        private String user;
        private String privateKeyFile;

        String getName() {
            return name;
        }

        String getUser() {
            return user;
        }

        String getPrivateKeyFile() {
            return privateKeyFile;
        }
    }
}