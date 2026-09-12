# isExcludedFromBackupKey

A key for indicating whether the system excludes the resource from all backups of app data.

static let isExcludedFromBackupKey: URLResourceKey## Discussion

The value of this key is a read-write Boolean NSNumber object.

Use this property to exclude cache and other app support files that aren’t necessary in a backup. Set this property each time you save a file because some common file operations cause this property to reset to `false`.

