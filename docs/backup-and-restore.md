# Backup and restore operations

Backups are proprietary local data. They are stored on the Debian development
host under ignored `backups/` by default and are never committed or bundled in
the APK. The tablet remains the source; ISO files are not copied into backups.

## Create a backup

1. Inside Windows, use **Start → Shut Down** and wait for shutdown to finish.
2. Stop RoboWindows. The backup command refuses to proceed while its process is
   running.
3. Find the machine ID from app-private profile metadata or a documented local
   inventory; do not place personal IDs in public documentation.
4. On Debian run:

```sh
scripts/backup-device-profile.sh \
  --machine-id MACHINE_ID \
  --name descriptive-name-YYYYMMDD
```

The tool computes the source checksum on Android, streams and compresses the
disk on Debian, copies configuration/metadata, verifies decompressed size and
SHA-256, then atomically publishes the directory. It refuses to overwrite an
existing destination.

## Verify a backup

```sh
scripts/verify-backup.sh backups/descriptive-name-YYYYMMDD
```

Verification checks every file listed in `SHA256SUMS`, gzip integrity, and the
decompressed disk size and SHA-256 recorded in `manifest.txt`.

## Restore policy

Restoration is intentionally not automated yet because choosing the wrong
package or machine ID could overwrite the working guest. Before restoring:

- stop RoboWindows;
- verify the backup;
- preserve or separately back up the current tablet disk;
- decompress to a temporary Debian file;
- verify its raw checksum again;
- check absolute package and machine paths in `launch.conf` and metadata;
- copy only to the explicitly selected app-private machine directory.

Automated restore requires its own specification, confirmation UI, and rollback
path before it is safe to add.
