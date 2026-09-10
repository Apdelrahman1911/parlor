#import "ProtectionSampler.h"
#import <TargetConditionals.h>
#import <objc/runtime.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <mach-o/dyld.h>
#include <mach-o/loader.h>
#include <mach/vm_prot.h>
#include <string.h>
#include <sys/attr.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <unistd.h>

static void demand(BOOL value, NSString *reason) {
    if (!value) @throw [NSException exceptionWithName:@"ProtectionBoundary" reason:reason userInfo:nil];
}

static NSDictionary *errorRecord(NSError *error) {
    return @{@"present": error != nil ? @YES : @NO, @"code": error ? @(error.code) : @0,
        @"domain": !error ? @"none" : [error.domain isEqual:NSCocoaErrorDomain] ? @"cocoa" :
            [error.domain isEqual:NSPOSIXErrorDomain] ? @"posix" : @"other"};
}

NSDictionary *ParlorProtectionImageIdentity(const void *address) {
    Dl_info info = {0};
    demand(address && dladdr(address, &info) && info.dli_fbase && info.dli_fname, @"image-resolution");
    const struct mach_header_64 *header = info.dli_fbase;
    demand(header->magic == MH_MAGIC_64 && header->ncmds <= 4096 && header->sizeofcmds <= 1024 * 1024,
           @"image-header");
    uint32_t count = _dyld_image_count(), matches = 0;
    demand(count > 0 && count <= 4096, @"image-count");
    intptr_t slide = 0;
    for (uint32_t i = 0; i < count; i++) {
        if ((const void *)_dyld_get_image_header(i) == info.dli_fbase) {
            matches++; slide = _dyld_get_image_vmaddr_slide(i);
        }
    }
    demand(matches == 1, @"image-loaded-identity");
    const uint8_t *commands = (const uint8_t *)(header + 1);
    size_t offset = 0;
    NSString *uuid = nil;
    NSMutableArray *platforms = [NSMutableArray array];
    id dylib = NSNull.null;
    BOOL executableAddress = NO;
    for (uint32_t i = 0; i < header->ncmds; i++) {
        demand(offset + sizeof(struct load_command) <= header->sizeofcmds, @"image-command-bounds");
        const struct load_command *command = (const void *)(commands + offset);
        demand(command->cmdsize >= sizeof(*command) && command->cmdsize <= header->sizeofcmds - offset,
               @"image-command-size");
        if (command->cmd == LC_UUID) {
            demand(!uuid && command->cmdsize == sizeof(struct uuid_command), @"image-uuid");
            uuid = [[[NSUUID alloc] initWithUUIDBytes:((const struct uuid_command *)command)->uuid]
                UUIDString].lowercaseString;
        } else if (command->cmd == LC_BUILD_VERSION) {
            demand(command->cmdsize >= sizeof(struct build_version_command), @"image-platform");
            [platforms addObject:@(((const struct build_version_command *)command)->platform)];
        } else if (command->cmd == LC_ID_DYLIB) {
            demand(command->cmdsize >= sizeof(struct dylib_command) && dylib == NSNull.null, @"image-dylib");
            struct dylib value = ((const struct dylib_command *)command)->dylib;
            dylib = @{@"current_version": @(value.current_version), @"compatibility_version": @(value.compatibility_version)};
        } else if (command->cmd == LC_SEGMENT_64) {
            demand(command->cmdsize >= sizeof(struct segment_command_64), @"image-segment");
            const struct segment_command_64 *segment = (const void *)command;
            uintptr_t start = (uintptr_t)(segment->vmaddr + slide), pointer = (uintptr_t)address;
            if ((segment->initprot & VM_PROT_EXECUTE) && pointer >= start && pointer - start < segment->vmsize)
                executableAddress = YES;
        }
        offset += command->cmdsize;
    }
    demand(offset == header->sizeofcmds && uuid && platforms.count == 1 && executableAddress &&
           (uintptr_t)address >= (uintptr_t)header, @"image-executable-address");
    NSString *name = [[NSString stringWithUTF8String:info.dli_fname] lastPathComponent];
    demand(name.length > 0 && name.length <= 256, @"image-name");
    return @{@"image_basename": name, @"uuid": uuid, @"platforms": platforms,
        @"cputype": @(header->cputype), @"cpusubtype": @(header->cpusubtype),
        @"image_offset": @((uintptr_t)address - (uintptr_t)header), @"dylib": dylib};
}

NSDictionary *ParlorProtectionImplementation(id receiver, SEL selector) {
    Class concrete = object_getClass(receiver);
    Method method = class_getInstanceMethod(concrete, selector);
    demand(concrete && method, @"resolved-method");
    IMP implementation = method_getImplementation(method);
    demand(implementation != NULL, @"method-implementation");
    return @{@"receiver_class": NSStringFromClass(concrete), @"selector": NSStringFromSelector(selector),
        @"implementation": ParlorProtectionImageIdentity((const void *)implementation)};
}

static NSDictionary *identity(struct stat value) {
    return @{@"device": @((uint64_t)value.st_dev), @"inode": @((uint64_t)value.st_ino),
        @"uid": @(value.st_uid), @"mode": @(value.st_mode), @"links": @(value.st_nlink),
        @"size": @(value.st_size), @"type": S_ISREG(value.st_mode) ? @"regular" : @"directory"};
}

static void attest(NSString *path, int fd, NSDictionary *expected) {
    struct stat named, opened;
    demand(lstat(path.fileSystemRepresentation, &named) == 0 && fstat(fd, &opened) == 0 &&
           [identity(named) isEqual:expected] && [identity(opened) isEqual:expected], @"same-inode-attestation");
}

static NSString *protection(id value, BOOL url) {
    if (!value) return @"missing";
    if (value == NSNull.null) return @"null";
    if (![value isKindOfClass:NSString.class]) return @"non-string";
    if ([value isEqual:(url ? NSURLFileProtectionComplete : NSFileProtectionComplete)]) return @"complete";
    if ([value isEqual:(url ? NSURLFileProtectionNone : NSFileProtectionNone)]) return @"none";
    if ([value isEqual:(url ? NSURLFileProtectionCompleteUnlessOpen : NSFileProtectionCompleteUnlessOpen)]) return @"unless-open";
    if ([value isEqual:(url ? NSURLFileProtectionCompleteUntilFirstUserAuthentication : NSFileProtectionCompleteUntilFirstUserAuthentication)]) return @"until-first-authentication";
#if TARGET_OS_IOS
    if (@available(iOS 17.0, *)) {
        if ([value isEqual:(url ? NSURLFileProtectionCompleteWhenUserInactive : NSFileProtectionCompleteWhenUserInactive)]) return @"when-user-inactive";
    }
#endif
    return @"other-string";
}

static NSDictionary *foundationQuery(NSString *path, BOOL directory, BOOL fileManager) {
    NSError *error = nil;
    id receiver = fileManager ? NSFileManager.defaultManager : [NSURL fileURLWithPath:path isDirectory:directory];
    SEL selector = fileManager ? @selector(attributesOfItemAtPath:error:) : @selector(resourceValuesForKeys:error:);
    NSDictionary *before = ParlorProtectionImplementation(receiver, selector);
    NSDictionary *values = fileManager ? [receiver attributesOfItemAtPath:path error:&error] :
        [receiver resourceValuesForKeys:directory ? @[NSURLVolumeSupportsFileProtectionKey] :
            @[NSURLFileProtectionKey, NSURLVolumeSupportsFileProtectionKey] error:&error];
    NSDictionary *after = ParlorProtectionImplementation(receiver, selector);
    demand([before isEqual:after], @"getter-implementation-changed");
    id raw = values[fileManager ? NSFileProtectionKey : NSURLFileProtectionKey];
    NSMutableDictionary *result = [@{@"dictionary_present": values != nil ? @YES : @NO, @"key_present": raw != nil ? @YES : @NO,
        @"protection": (!fileManager && directory) ? @"NOT_APPLICABLE_DIRECTORY" : protection(raw, !fileManager),
        @"native_error": errorRecord(error), @"implementation_before": before, @"implementation_after": after} mutableCopy];
    if (!fileManager) {
        id volume = values[NSURLVolumeSupportsFileProtectionKey];
        result[@"volume_support"] = [volume isKindOfClass:NSNumber.class] ?
            ([volume boolValue] ? @"supported" : @"unsupported") : volume ? @"non-number" : @"missing";
        result[@"fresh_url"] = @YES;
        result[@"is_directory"] = @(directory);
    }
    return result;
}

static NSDictionary *descriptorClass(int fd) {
#if defined(F_GETPROTECTIONCLASS)
    errno = 0;
    int result = fcntl(fd, F_GETPROTECTIONCLASS);
    int code = result == -1 ? errno : 0;
    return @{@"sdk_available": @YES, @"command": @(F_GETPROTECTIONCLASS), @"return_value": @(result),
        @"errno": @(code), @"class": result == -1 ? NSNull.null : @(result)};
#else
    return @{@"sdk_available": @NO, @"status": @"PUBLIC_SDK_SYMBOL_UNAVAILABLE"};
#endif
}

static NSDictionary *descriptorAttributes(int fd) {
#if defined(ATTR_CMN_RETURNED_ATTRS) && defined(ATTR_CMN_DATA_PROTECT_FLAGS)
    struct attrlist requested = {0};
    requested.bitmapcount = ATTR_BIT_MAP_COUNT;
    requested.commonattr = ATTR_CMN_RETURNED_ATTRS | ATTR_CMN_DATA_PROTECT_FLAGS;
    struct __attribute__((packed, aligned(4))) {
        uint32_t length;
        attribute_set_t returned;
        uint32_t protection;
    } output = {0};
    errno = 0;
    int result = fgetattrlist(fd, &requested, &output, sizeof(output), 0);
    int code = result == -1 ? errno : 0;
    BOOL present = result == 0 && (output.returned.commonattr & ATTR_CMN_DATA_PROTECT_FLAGS) != 0;
    if (result == 0) {
        demand(output.length == sizeof(uint32_t) + sizeof(attribute_set_t) + (present ? sizeof(uint32_t) : 0) &&
            !(output.returned.commonattr & ~(ATTR_CMN_RETURNED_ATTRS | ATTR_CMN_DATA_PROTECT_FLAGS)) &&
            !output.returned.volattr && !output.returned.dirattr && !output.returned.fileattr && !output.returned.forkattr,
            @"returned-attribute-layout");
    }
    return @{@"sdk_available": @YES, @"return_value": @(result), @"errno": @(code),
        @"length": @(output.length), @"returned_common_mask": @(output.returned.commonattr),
        @"requested_common_mask": @(requested.commonattr), @"data_protection_mask": @(ATTR_CMN_DATA_PROTECT_FLAGS),
        @"attribute_set_bytes": @(sizeof(attribute_set_t)),
        @"returned_attributes_mask": @(ATTR_CMN_RETURNED_ATTRS), @"protection_returned": @(present),
        @"class": present ? @(output.protection) : NSNull.null};
#else
    return @{@"sdk_available": @NO, @"status": @"PUBLIC_SDK_SYMBOL_UNAVAILABLE"};
#endif
}

static NSDictionary *descriptorFilesystem(int fd) {
    struct statfs value = {0};
    errno = 0;
    int result = fstatfs(fd, &value), code = result == -1 ? errno : 0;
    NSMutableDictionary *record = [@{@"return_value": @(result), @"errno": @(code)} mutableCopy];
    if (result == 0) {
        demand(strnlen(value.f_fstypename, sizeof(value.f_fstypename)) < sizeof(value.f_fstypename), @"filesystem-type");
        record[@"type"] = [NSString stringWithUTF8String:value.f_fstypename];
        record[@"fsid"] = @[@(value.f_fsid.val[0]), @(value.f_fsid.val[1])];
        record[@"flags"] = @(value.f_flags);
#if defined(MNT_CPROTECT)
        record[@"content_protection_capability"] = (value.f_flags & MNT_CPROTECT) ? @"supported" : @"unsupported";
        record[@"content_protection_flag"] = @(MNT_CPROTECT);
#else
        record[@"content_protection_capability"] = @"PUBLIC_SDK_SYMBOL_UNAVAILABLE";
#endif
    }
    return record;
}

NSDictionary *ParlorProtectionSample(NSString *path) {
    int fd = -1;
    NSMutableDictionary *record = [@{@"schema": @1, @"collection_status": @"FAIL", @"reads": [NSMutableArray array]} mutableCopy];
    @try {
        demand(path.isAbsolutePath && [path isEqual:path.stringByStandardizingPath] && getuid() > 0 && getuid() == geteuid(), @"sample-path");
        struct stat named;
        demand(lstat(path.fileSystemRepresentation, &named) == 0 && named.st_uid == getuid() &&
            (S_ISREG(named.st_mode) || S_ISDIR(named.st_mode)) && named.st_nlink >= 1 &&
            (!S_ISREG(named.st_mode) || named.st_nlink == 1) && named.st_size >= 0 && named.st_size <= 16 * 1024 * 1024,
            @"sample-type-owner-links-size");
        BOOL directory = S_ISDIR(named.st_mode);
        NSDictionary *expected = identity(named);
        record[@"identity"] = expected;
        fd = open(path.fileSystemRepresentation, O_RDONLY | O_NOFOLLOW | O_CLOEXEC | (directory ? O_DIRECTORY : 0));
        demand(fd >= 0, @"sample-open");
        attest(path, fd, expected);
        for (NSString *kind in @[@"fm", @"url", @"fcntl", @"attrlist", @"filesystem"]) {
            attest(path, fd, expected);
            NSDictionary *value = [kind isEqual:@"fm"] ? foundationQuery(path, directory, YES) :
                [kind isEqual:@"url"] ? foundationQuery(path, directory, NO) :
                [kind isEqual:@"fcntl"] ? descriptorClass(fd) :
                [kind isEqual:@"attrlist"] ? descriptorAttributes(fd) : descriptorFilesystem(fd);
            attest(path, fd, expected);
            record[kind] = value;
            [record[@"reads"] addObject:@{@"kind": kind, @"before": expected, @"after": expected,
                @"descriptor_and_path_same_inode": @YES}];
        }
        record[@"collection_status"] = @"PASS";
    } @catch (NSException *exception) {
        record[@"failure"] = [exception.name isEqual:@"ProtectionBoundary"] ? exception.reason : @"native-exception";
    } @finally {
        if (fd >= 0) {
            int result = close(fd);
            record[@"descriptor_closed"] = result == 0 ? @YES : @NO;
            if (result != 0) record[@"collection_status"] = @"FAIL";
        } else record[@"descriptor_closed"] = @NO;
    }
    return record;
}
