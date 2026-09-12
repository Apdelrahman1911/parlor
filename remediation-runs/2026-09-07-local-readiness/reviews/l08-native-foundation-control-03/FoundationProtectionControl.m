// Isolated diagnostic, not Parlor source. Compile only for the approved Simulator.
// No app installation, Keychain, defaults, player state, or production path access.
#import <Foundation/Foundation.h>
#import <TargetConditionals.h>
#import <objc/runtime.h>
#include <mach-o/dyld.h>
#include <mach-o/loader.h>
#include <sys/stat.h>
#include <signal.h>
#include <unistd.h>
#include <errno.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include "OwnedControlConfig.h"

#if !TARGET_OS_SIMULATOR || !TARGET_OS_IOS || !defined(__arm64__)
#error This diagnostic requires an arm64 iOS Simulator target, never host macOS.
#endif

static NSMutableArray<NSDictionary *> *rows;
static NSString *root;
static dev_t rootDevice;
static ino_t rootInode;
static dev_t directoryDevice;
static ino_t directoryInode;
static BOOL directoryPinned;

static void requireControl(BOOL condition, NSString *closedReason) {
    if (!condition) @throw [NSException exceptionWithName:@"ControlBoundary"
                                                reason:closedReason userInfo:nil];
}

static NSDictionary *nativeError(NSError *error) {
    return @{@"present": (error != nil ? @YES : @NO), @"domain": error == nil ? @"none" :
        ([error.domain isEqual:NSCocoaErrorDomain] ? @"cocoa" :
         ([error.domain isEqual:NSPOSIXErrorDomain] ? @"posix" : @"other")),
        @"code": error == nil ? @0 : @(error.code)};
}

static void operation(NSString *identifier, BOOL (^body)(NSError **)) {
    requireControl(rows.count < 32, @"row-limit");
    @try {
        NSError *error = nil;
        BOOL result = body(&error);
        [rows addObject:@{@"id": identifier, @"kind": @"operation",
            @"returned": @YES, @"result": @(result), @"exception": @"none",
            @"native_error": nativeError(error)}];
    } @catch (NSException *exception) {
        if ([exception.name isEqual:@"ControlBoundary"]) @throw;
        [rows addObject:@{@"id": identifier, @"kind": @"operation",
            @"returned": @NO, @"result": @NO, @"exception": @"objc-exception",
            @"native_error": nativeError(nil)}];
    }
}

static NSString *protectionName(id value, BOOL urlKey) {
    if (value == [NSNull null]) return @"null";
    if (![value isKindOfClass:[NSString class]]) return @"non-string";
    if ([value isEqual:(urlKey ? NSURLFileProtectionComplete : NSFileProtectionComplete)]) return @"complete";
    if ([value isEqual:(urlKey ? NSURLFileProtectionNone : NSFileProtectionNone)]) return @"none";
    if ([value isEqual:(urlKey ? NSURLFileProtectionCompleteUnlessOpen : NSFileProtectionCompleteUnlessOpen)]) return @"unless-open";
    if ([value isEqual:(urlKey ? NSURLFileProtectionCompleteUntilFirstUserAuthentication : NSFileProtectionCompleteUntilFirstUserAuthentication)]) return @"until-first-authentication";
    if (@available(iOS 17.0, *)) {
        if ([value isEqual:(urlKey ? NSURLFileProtectionCompleteWhenUserInactive : NSFileProtectionCompleteWhenUserInactive)]) return @"when-user-inactive";
    }
    return @"other-string";
}

static NSDictionary *query(NSString *path, NSString *kind) {
    @try {
        NSError *error = nil;
        NSString *key = [kind isEqual:@"fm"] ? NSFileProtectionKey :
            ([kind isEqual:@"url"] ? NSURLFileProtectionKey :
             ([kind isEqual:@"backup"] ? NSURLIsExcludedFromBackupKey : NSURLVolumeSupportsFileProtectionKey));
        NSDictionary *values = [kind isEqual:@"fm"] ?
            [[NSFileManager defaultManager] attributesOfItemAtPath:path error:&error] :
            [[NSURL fileURLWithPath:path] resourceValuesForKeys:@[key] error:&error];
        id value = values[key];
        NSString *summary = values == nil ? @"unavailable" : value == nil ? @"missing" :
            (([kind isEqual:@"volume"] || [kind isEqual:@"backup"]) ? ([value isKindOfClass:[NSNumber class]] ?
                ([kind isEqual:@"backup"] ? ([value boolValue] ? @"excluded" : @"included") :
                 ([value boolValue] ? @"supported" : @"unsupported")) : @"non-number") :
                protectionName(value, [kind isEqual:@"url"]));
        return @{@"returned": @YES, @"dictionary_present": (values != nil ? @YES : @NO),
            @"key_present": (value != nil ? @YES : @NO), @"value": summary,
            @"exception": @"none", @"native_error": nativeError(error)};
    } @catch (__unused NSException *exception) {
        return @{@"returned": @NO, @"dictionary_present": @NO,
            @"key_present": @NO, @"value": @"unobserved",
            @"exception": @"objc-exception", @"native_error": nativeError(nil)};
    }
}

static void attestRoot(void) {
    struct stat value;
    requireControl(lstat(root.fileSystemRepresentation, &value) == 0 &&
        S_ISDIR(value.st_mode) && value.st_uid == getuid() &&
        value.st_dev == rootDevice && value.st_ino == rootInode &&
        (value.st_mode & 0777) == 0700, @"root-identity");
}

// No enumeration, arbitrary names, symlinks, parent components, or fallback roots.
static NSString *ownedPath(NSString *leaf, BOOL directory, BOOL mustBeAbsent) {
    attestRoot();
    requireControl([@[@"created", @"created/atomic.bin", @"created/direct.bin",
                      @"created/set.bin"] containsObject:leaf], @"unknown-leaf");
    if (![leaf isEqual:@"created"]) {
        struct stat parent;
        NSString *parentPath = [root stringByAppendingPathComponent:@"created"];
        requireControl(directoryPinned && lstat(parentPath.fileSystemRepresentation, &parent) == 0 &&
            S_ISDIR(parent.st_mode) && parent.st_uid == getuid() &&
            parent.st_dev == directoryDevice && parent.st_ino == directoryInode &&
            (parent.st_mode & 0777) == 0700,
            @"parent-identity");
    }
    NSString *path = [root stringByAppendingPathComponent:leaf];
    struct stat value;
    int result = lstat(path.fileSystemRepresentation, &value);
    if (result != 0) {
        requireControl(errno == ENOENT, @"entry-stat");
    } else {
        requireControl(!mustBeAbsent && value.st_uid == getuid() && value.st_dev == rootDevice &&
            (directory ? S_ISDIR(value.st_mode) : S_ISREG(value.st_mode)) &&
            (directory || (value.st_nlink == 1 && value.st_size >= 0 && value.st_size <= 256)),
            @"entry-identity");
        if (directory && directoryPinned) {
            requireControl(value.st_dev == directoryDevice && value.st_ino == directoryInode,
                @"directory-identity");
        }
    }
    return path;
}

static void observe(NSString *identifier, NSString *leaf, BOOL directory) {
    NSString *path = ownedPath(leaf, directory, NO);
    NSMutableDictionary *row = [@{@"id": identifier, @"kind": @"observation",
        @"fm": query(path, @"fm"), @"volume": query(path, @"volume"),
        @"backup": query(path, @"backup")} mutableCopy];
    // NSURL.h scopes NSURLFileProtectionKey to regular files, not directories.
    if (!directory) row[@"url"] = query(path, @"url");
    requireControl(rows.count < 32, @"row-limit");
    [rows addObject:row];
}

static NSDictionary *imageIdentity(const struct mach_header *raw) {
    if (raw == NULL || raw->magic != MH_MAGIC_64) return @{@"status": @"unavailable"};
    const struct mach_header_64 *header = (const struct mach_header_64 *)raw;
    if (header->ncmds > 4096 || header->sizeofcmds > 1024 * 1024) return @{@"status": @"unavailable"};
    const uint8_t *start = (const uint8_t *)(header + 1);
    size_t offset = 0;
    NSString *uuid = nil;
    uint32_t platform = 0;
    for (uint32_t index = 0; index < header->ncmds; index++) {
        if (offset + sizeof(struct load_command) > header->sizeofcmds) return @{@"status": @"unavailable"};
        const struct load_command *command = (const struct load_command *)(start + offset);
        if (command->cmdsize < sizeof(*command) || command->cmdsize > header->sizeofcmds - offset) return @{@"status": @"unavailable"};
        if (command->cmd == LC_UUID && command->cmdsize >= sizeof(struct uuid_command)) {
            if (uuid != nil) return @{@"status": @"unavailable"};
            uuid = [[[NSUUID alloc] initWithUUIDBytes:((const struct uuid_command *)command)->uuid] UUIDString];
        }
        if (command->cmd == LC_BUILD_VERSION && command->cmdsize >= sizeof(struct build_version_command)) {
            platform = ((const struct build_version_command *)command)->platform;
        }
        offset += command->cmdsize;
    }
    return uuid == nil ? @{@"status": @"unavailable"} :
        @{@"status": @"observed", @"uuid": uuid.lowercaseString, @"platform": @(platform)};
}

static NSDictionary *foundationIdentity(void) {
    const char *classPath = class_getImageName([NSFileManager class]);
    if (classPath == NULL || _dyld_image_count() > 4096) return @{@"status": @"unavailable"};
    for (uint32_t index = 0; index < _dyld_image_count(); index++) {
        const char *path = _dyld_get_image_name(index);
        if (path != NULL && strcmp(path, classPath) == 0) return imageIdentity(_dyld_get_image_header(index));
    }
    return @{@"status": @"unavailable"};
}

static void collect(void) {
    NSFileManager *files = [NSFileManager defaultManager];
    NSString *directory = ownedPath(@"created", YES, YES);
    operation(@"directory-create", ^BOOL(NSError **error) {
        return [files createDirectoryAtPath:directory withIntermediateDirectories:YES
            attributes:@{NSFileProtectionKey: NSFileProtectionComplete} error:error];
    });
    struct stat created;
    requireControl(lstat(directory.fileSystemRepresentation, &created) == 0 &&
        S_ISDIR(created.st_mode) && created.st_uid == getuid() &&
        created.st_dev == rootDevice, @"created-directory-identity");
    directoryDevice = created.st_dev; directoryInode = created.st_ino; directoryPinned = YES;
    observe(@"directory-after-create", @"created", YES);
    operation(@"directory-backup", ^BOOL(NSError **error) {
        return [[NSURL fileURLWithPath:ownedPath(@"created", YES, NO) isDirectory:YES]
            setResourceValue:@YES forKey:NSURLIsExcludedFromBackupKey error:error];
    });
    observe(@"directory-after-backup", @"created", YES);
    const char payload[] = "PUBLIC SYNTHETIC FOUNDATION CONTROL ONLY";
    NSData *data = [NSData dataWithBytes:payload length:sizeof(payload) - 1];
    NSString *atomic = ownedPath(@"created/atomic.bin", NO, YES);
    operation(@"atomic-write", ^BOOL(NSError **error) {
        return [data writeToFile:atomic options:(NSDataWritingAtomic | NSDataWritingFileProtectionComplete) error:error];
    });
    observe(@"atomic-after-write", @"created/atomic.bin", NO);
    operation(@"atomic-backup", ^BOOL(NSError **error) {
        return [[NSURL fileURLWithPath:ownedPath(@"created/atomic.bin", NO, NO)]
            setResourceValue:@YES forKey:NSURLIsExcludedFromBackupKey error:error];
    });
    observe(@"atomic-after-backup", @"created/atomic.bin", NO);
    // Explicit directory setter is deliberately AFTER the production-like
    // create -> backup -> atomic Complete write -> backup observations.
    operation(@"directory-set", ^BOOL(NSError **error) {
        return [files setAttributes:@{NSFileProtectionKey: NSFileProtectionComplete}
            ofItemAtPath:ownedPath(@"created", YES, NO) error:error];
    });
    observe(@"directory-after-set", @"created", YES);
    NSString *direct = ownedPath(@"created/direct.bin", NO, YES);
    operation(@"direct-write", ^BOOL(NSError **error) {
        return [data writeToFile:direct options:NSDataWritingFileProtectionComplete error:error];
    });
    observe(@"direct-after-write", @"created/direct.bin", NO);
    NSString *set = ownedPath(@"created/set.bin", NO, YES);
    operation(@"default-write", ^BOOL(NSError **error) {
        return [data writeToFile:set options:NSDataWritingAtomic error:error];
    });
    observe(@"file-before-set", @"created/set.bin", NO);
    operation(@"file-set", ^BOOL(NSError **error) {
        return [files setAttributes:@{NSFileProtectionKey: NSFileProtectionComplete}
            ofItemAtPath:ownedPath(@"created/set.bin", NO, NO) error:error];
    });
    observe(@"file-after-set", @"created/set.bin", NO);
}

int main(void) {
    // A stuck Foundation call cannot leave this diagnostic running indefinitely.
    signal(SIGALRM, SIG_DFL);
    alarm(20);
    umask(0077);
    @autoreleasepool {
        rows = [NSMutableArray array];
        root = @CONTROL_FIXTURE_ROOT;
        NSMutableDictionary *receipt = [@{@"schema_version": @1,
            @"run_token": @CONTROL_RUN_TOKEN, @"source_sha256": @CONTROL_SOURCE_SHA256,
            @"simulator_only": @YES, @"app_container": @NO,
            @"hardware_protection_verified": @NO, @"l08_requirements_waived": @NO,
            @"pid": @(getpid()), @"rows": rows} mutableCopy];
        int status = 2;
        @try {
            char canonical[PATH_MAX];
            requireControl(realpath(root.fileSystemRepresentation, canonical) != NULL &&
                [root isEqualToString:[NSString stringWithUTF8String:canonical]], @"root-canonical");
            struct stat value;
            requireControl(lstat(root.fileSystemRepresentation, &value) == 0 &&
                S_ISDIR(value.st_mode) && value.st_uid == getuid() &&
                (value.st_mode & 0777) == 0700 && value.st_dev == (dev_t)CONTROL_ROOT_DEVICE &&
                value.st_ino == (ino_t)CONTROL_ROOT_INODE, @"root-initial-identity");
            rootDevice = value.st_dev; rootInode = value.st_ino;
            NSDictionary *environment = [NSProcessInfo processInfo].environment;
            requireControl([environment[@"SIMULATOR_UDID"] isEqual:@CONTROL_DEVICE_UDID], @"simulator-identity");
            requireControl([environment[@"PARLOR_CONTROL_TOKEN"] isEqual:@CONTROL_RUN_TOKEN], @"launch-token");
            NSString *temporary = NSTemporaryDirectory();
            requireControl(temporary != nil && realpath(temporary.fileSystemRepresentation, canonical) != NULL &&
                [[NSString stringWithUTF8String:canonical] isEqualToString:@CONTROL_TEMP_ROOT], @"temporary-root");
            requireControl(lstat(canonical, &value) == 0 && S_ISDIR(value.st_mode) &&
                value.st_uid == getuid() && value.st_dev == (dev_t)CONTROL_TEMP_DEVICE &&
                value.st_ino == (ino_t)CONTROL_TEMP_INODE && (value.st_mode & 0777) == 0700,
                @"temporary-identity");
            NSOperatingSystemVersion version = [NSProcessInfo processInfo].operatingSystemVersion;
            requireControl(version.majorVersion == 26 && version.minorVersion == 5 && version.patchVersion == 0, @"runtime-version");
            receipt[@"runtime_version"] = @[@(version.majorVersion), @(version.minorVersion), @(version.patchVersion)];
            receipt[@"main_image"] = imageIdentity(_dyld_get_image_header(0));
            receipt[@"foundation_image"] = foundationIdentity();
            collect();
            receipt[@"status"] = @"OBSERVATIONS_COMPLETE";
            status = 0; // Collection complete, NOT a privacy/App/Store test PASS.
        } @catch (NSException *exception) {
            receipt[@"status"] = @"CONTROL_ERROR";
            // Only locally thrown closed reason values; never native exception descriptions.
            receipt[@"reason"] = [exception.name isEqual:@"ControlBoundary"] ? exception.reason : @"objc-exception";
        }
        NSError *error = nil;
        NSData *encoded = [NSJSONSerialization dataWithJSONObject:receipt options:NSJSONWritingSortedKeys error:&error];
        if (encoded == nil || encoded.length > 32768) return 3;
        const uint8_t *bytes = encoded.bytes;
        size_t remaining = encoded.length;
        while (remaining != 0) {
            ssize_t written = write(STDOUT_FILENO, bytes, remaining);
            if (written <= 0) return 3;
            bytes += written; remaining -= (size_t)written;
        }
        if (write(STDOUT_FILENO, "\n", 1) != 1) return 3;
        return status;
    }
}
