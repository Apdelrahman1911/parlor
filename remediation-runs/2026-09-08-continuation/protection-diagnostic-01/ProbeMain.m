// Standalone, public-data-only iOS Simulator diagnostic; never an app test.
#import "ProtectionSampler.h"
#import <TargetConditionals.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <sys/attr.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <unistd.h>
#include "OwnedProbeContext.h"

#if !TARGET_OS_IOS || !TARGET_OS_SIMULATOR || !defined(__arm64__)
#error This entry point is only for the explicitly owned arm64 iOS Simulator.
#endif

static NSDictionary *directoryIdentity;
static NSMutableDictionary *samples;

static void demand(BOOL value, NSString *reason) {
    if (!value) @throw [NSException exceptionWithName:@"ProtectionBoundary" reason:reason userInfo:nil];
}

static void emit(NSDictionary *row) {
    NSData *data = [NSJSONSerialization dataWithJSONObject:row options:NSJSONWritingSortedKeys error:NULL];
    demand(data && data.length <= 128 * 1024 && fwrite(data.bytes, 1, data.length, stdout) == data.length &&
           fputc('\n', stdout) != EOF && fflush(stdout) == 0, @"output-write");
}

static NSDictionary *errorRecord(NSError *error) {
    return @{@"present": @(error != nil), @"code": error ? @(error.code) : @0,
        @"domain": !error ? @"none" : [error.domain isEqual:NSCocoaErrorDomain] ? @"cocoa" :
            [error.domain isEqual:NSPOSIXErrorDomain] ? @"posix" : @"other"};
}

static void attestRoot(void) {
    struct stat value;
    demand(lstat([PROBE_ROOT fileSystemRepresentation], &value) == 0 && S_ISDIR(value.st_mode) &&
        value.st_uid == getuid() && getuid() > 0 && getuid() == geteuid() && (value.st_mode & 0777) == 0700 &&
        (uint64_t)value.st_dev == PROBE_ROOT_DEVICE && (uint64_t)value.st_ino == PROBE_ROOT_INODE,
        @"owned-root-custody");
}

static NSString *ownedPath(NSString *leaf, BOOL absent) {
    attestRoot();
    demand([@[@"created", @"created/complete.bin", @"created/none.bin", @"created/default.bin"] containsObject:leaf],
           @"closed-fixture-name");
    if (![leaf isEqual:@"created"]) {
        struct stat parent;
        demand(directoryIdentity && lstat([[PROBE_ROOT stringByAppendingPathComponent:@"created"] fileSystemRepresentation], &parent) == 0 &&
            S_ISDIR(parent.st_mode) && parent.st_uid == getuid() && (parent.st_mode & 0777) == 0700 &&
            (uint64_t)parent.st_dev == [directoryIdentity[@"device"] unsignedLongLongValue] &&
            (uint64_t)parent.st_ino == [directoryIdentity[@"inode"] unsignedLongLongValue], @"owned-directory-custody");
    }
    NSString *path = [PROBE_ROOT stringByAppendingPathComponent:leaf];
    struct stat value;
    int result = lstat(path.fileSystemRepresentation, &value), code = result == -1 ? errno : 0;
    demand(absent ? result == -1 && code == ENOENT : result == 0 && value.st_uid == getuid() &&
        (uint64_t)value.st_dev == PROBE_ROOT_DEVICE &&
        ([leaf isEqual:@"created"] ? S_ISDIR(value.st_mode) : S_ISREG(value.st_mode) && value.st_nlink == 1 &&
            value.st_size >= 0 && value.st_size <= 256), @"owned-entry-custody");
    return path;
}

static NSDictionary *sample(NSString *identifier, NSString *leaf) {
    NSDictionary *row = ParlorProtectionSample(ownedPath(leaf, NO));
    samples[identifier] = row;
    emit(@{@"kind": @"sample", @"id": identifier, @"sample": row});
    demand([row[@"collection_status"] isEqual:@"PASS"] && [row[@"descriptor_closed"] boolValue], @"sample-collection");
    return row;
}

static void writeFile(NSString *identifier, NSString *leaf, NSDataWritingOptions flags, BOOL replacement) {
    NSString *path = ownedPath(leaf, !replacement);
    NSData *data = [(replacement ? @"Parlor public synthetic protection replacement v2" :
        @"Parlor public synthetic protection control v1") dataUsingEncoding:NSUTF8StringEncoding];
    SEL selector = @selector(writeToFile:options:error:);
    NSDictionary *before = ParlorProtectionImplementation(data, selector);
    NSError *error = nil;
    BOOL result = [data writeToFile:path options:flags error:&error];
    NSDictionary *after = ParlorProtectionImplementation(data, selector);
    emit(@{@"kind": @"operation", @"id": identifier, @"returned": @(result), @"native_error": errorRecord(error),
        @"requested_options": @(flags), @"payload_bytes": @(data.length),
        @"implementation_before": before, @"implementation_after": after,
        @"observer_descriptor_held_across_write": @NO});
    demand([before isEqual:after] && result && !error, @"owned-write");
}

static void kernelSet(NSString *path, NSDictionary *expected) {
#if defined(F_SETPROTECTIONCLASS) && defined(PROTECTION_CLASS_A)
    // No PRIVATE header or numeric class fallback. Only a public SDK declaration
    // can enable this mutation, exclusively on our pre-observed negative control.
    int fd = open(path.fileSystemRepresentation, O_RDWR | O_NOFOLLOW | O_CLOEXEC);
    demand(fd >= 0, @"negative-control-open");
    @try {
        struct stat opened, named;
        demand(fstat(fd, &opened) == 0 && lstat(path.fileSystemRepresentation, &named) == 0 &&
            S_ISREG(opened.st_mode) && opened.st_uid == getuid() && opened.st_nlink == 1 &&
            opened.st_dev == named.st_dev && opened.st_ino == named.st_ino &&
            (uint64_t)opened.st_dev == [expected[@"device"] unsignedLongLongValue] &&
            (uint64_t)opened.st_ino == [expected[@"inode"] unsignedLongLongValue] &&
            opened.st_size == [expected[@"size"] longLongValue], @"negative-control-inode");
        errno = 0;
        int result = fcntl(fd, F_SETPROTECTIONCLASS, PROTECTION_CLASS_A), code = result == -1 ? errno : 0;
        emit(@{@"kind": @"operation", @"id": @"none-kernel-set", @"sdk_available": @YES,
            @"command": @(F_SETPROTECTIONCLASS), @"sdk_class": @(PROTECTION_CLASS_A),
            @"return_value": @(result), @"errno": @(code)});
    } @finally {
        demand(close(fd) == 0, @"negative-control-close");
    }
#else
    (void)path; (void)expected;
    emit(@{@"kind": @"operation", @"id": @"none-kernel-set", @"sdk_available": @NO,
        @"status": @"PUBLIC_SDK_COMMAND_OR_CLASS_UNAVAILABLE"});
#endif
}

static NSDictionary *collect(void) {
    NSFileManager *manager = NSFileManager.defaultManager;
    SEL create = @selector(createDirectoryAtPath:withIntermediateDirectories:attributes:error:);
    NSDictionary *before = ParlorProtectionImplementation(manager, create);
    NSError *error = nil;
    BOOL result = [manager createDirectoryAtPath:ownedPath(@"created", YES) withIntermediateDirectories:NO
        attributes:@{NSFileProtectionKey: NSFileProtectionComplete, NSFilePosixPermissions: @0700} error:&error];
    NSDictionary *after = ParlorProtectionImplementation(manager, create);
    emit(@{@"kind": @"operation", @"id": @"directory-create", @"returned": @(result), @"native_error": errorRecord(error),
        @"requested_protection": @"complete", @"implementation_before": before, @"implementation_after": after});
    demand(result && !error && [before isEqual:after], @"owned-directory-create");
    directoryIdentity = sample(@"directory-baseline", @"created")[@"identity"];
    writeFile(@"complete-write", @"created/complete.bin", NSDataWritingAtomic | NSDataWritingFileProtectionComplete, NO);
    NSDictionary *original = sample(@"complete-baseline", @"created/complete.bin")[@"identity"];
    writeFile(@"none-write", @"created/none.bin", NSDataWritingAtomic | NSDataWritingFileProtectionNone, NO);
    NSDictionary *negative = sample(@"none-baseline", @"created/none.bin")[@"identity"];
    writeFile(@"default-write", @"created/default.bin", NSDataWritingAtomic, NO);
    sample(@"default-baseline", @"created/default.bin");

    // All observation FDs have closed. Holding the old inode can perturb the
    // atomic writer's EBUSY fallback; witness the transition without inducing it.
    writeFile(@"complete-replace", @"created/complete.bin", NSDataWritingAtomic | NSDataWritingFileProtectionComplete, YES);
    NSDictionary *replaced = sample(@"complete-after-replace", @"created/complete.bin")[@"identity"];
    demand(![original[@"size"] isEqual:replaced[@"size"]], @"replacement-size");

    kernelSet(ownedPath(@"created/none.bin", NO), negative);
    NSDictionary *afterKernel = sample(@"none-after-kernel-set", @"created/none.bin")[@"identity"];
    demand([negative isEqual:afterKernel], @"kernel-set-named-inode-changed");
    NSURL *url = [NSURL fileURLWithPath:ownedPath(@"created/none.bin", NO) isDirectory:NO];
    SEL set = @selector(setResourceValue:forKey:error:);
    before = ParlorProtectionImplementation(url, set);
    error = nil;
    result = [url setResourceValue:NSURLFileProtectionComplete forKey:NSURLFileProtectionKey error:&error];
    after = ParlorProtectionImplementation(url, set);
    emit(@{@"kind": @"operation", @"id": @"none-url-set", @"returned": @(result), @"native_error": errorRecord(error),
        @"requested_protection": @"complete", @"implementation_before": before, @"implementation_after": after});
    demand([before isEqual:after], @"setter-implementation-changed");
    demand([negative isEqual:sample(@"none-after-url-set", @"created/none.bin")[@"identity"]], @"url-set-named-inode-changed");
    unsigned pass = 0;
    NSArray *required = @[@"directory-baseline", @"complete-baseline", @"complete-after-replace", @"none-after-url-set"];
    for (NSString *identifier in required) {
        NSDictionary *fm = samples[identifier][@"fm"];
        if ([fm[@"dictionary_present"] boolValue] && [fm[@"key_present"] boolValue] &&
            [fm[@"protection"] isEqual:@"complete"] && ![fm[@"native_error"][@"present"] boolValue]) pass++;
    }
    return @{@"strict_synthetic_complete": @{@"required_sample_ids": required, @"pass": @(pass),
        @"fail": @(required.count - pass), @"status": pass == required.count ? @"PASS" : @"FAIL"},
        @"replacement": @{@"before": original, @"after": replaced, @"observer_descriptor_held": @NO,
            @"named_inode_changed": @(![original[@"inode"] isEqual:replaced[@"inode"]])}};
}

int main(void) {
    @autoreleasepool {
        signal(SIGALRM, SIG_DFL);
        alarm(25); // Self-only bound; no PID/PGID discovery or external signaling.
        samples = [NSMutableDictionary dictionary];
        NSMutableDictionary *final = [@{@"kind": @"final", @"schema": @1, @"collection_status": @"FAIL",
            @"scope": @"synthetic-native-metadata-only", @"production_snapshots_observed": @NO,
            @"historical_a37_strict_result_changed": @NO} mutableCopy];
        @try {
            attestRoot();
            const char *udid = getenv("SIMULATOR_UDID");
            demand(udid && [PROBE_UDID isEqualToString:[NSString stringWithUTF8String:udid]], @"actual-simulator-udid");
            NSOperatingSystemVersion version = NSProcessInfo.processInfo.operatingSystemVersion;
            demand(version.majorVersion == 26 && version.minorVersion == 2 && version.patchVersion == 0, @"actual-runtime-version");
            final[@"runtime_version"] = @[@(version.majorVersion), @(version.minorVersion), @(version.patchVersion)];
            final[@"context"] = @{@"source_sha": PROBE_SOURCE, @"control_sha256": PROBE_CONTROL,
                @"nonce": PROBE_NONCE, @"simulator_udid": PROBE_UDID,
                @"fixture_root_device": @(PROBE_ROOT_DEVICE), @"fixture_root_inode": @(PROBE_ROOT_INODE)};
            final[@"main_image_before"] = ParlorProtectionImageIdentity((const void *)&main);
            final[@"sdk_options"] = @{@"atomic": @(NSDataWritingAtomic), @"complete": @(NSDataWritingFileProtectionComplete),
                @"none": @(NSDataWritingFileProtectionNone)};
            [final addEntriesFromDictionary:collect()];
            attestRoot();
            final[@"main_image_after"] = ParlorProtectionImageIdentity((const void *)&main);
            demand([final[@"main_image_before"] isEqual:final[@"main_image_after"]], @"main-image-changed");
            final[@"collection_status"] = @"PASS";
        } @catch (NSException *exception) {
            final[@"failure"] = [exception.name isEqual:@"ProtectionBoundary"] ? exception.reason : @"native-exception";
        }
        emit(final);
        return [final[@"collection_status"] isEqual:@"PASS"] ? 0 : 1;
    }
}
