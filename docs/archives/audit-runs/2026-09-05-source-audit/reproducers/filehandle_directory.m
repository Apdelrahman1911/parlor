// Isolated Foundation API reproducer. Uses ONLY a caller-supplied task directory.
// Does not link Parlor or establish iOS-device/runtime evidence.
#import <Foundation/Foundation.h>
#include <stdio.h>
int main(int argc, const char **argv) {
    @autoreleasepool {
        if (argc != 2) return 2;
        NSString *path = [NSString stringWithUTF8String:argv[1]];
        NSFileHandle *handle = [NSFileHandle fileHandleForReadingAtPath:path];
        printf("opened_directory=%s\n", handle ? "true" : "false");
        if (!handle) return 3;
        @try {
            NSData *data = [handle readDataOfLength:9];
            printf("read_bytes=%lu\n", (unsigned long)data.length);
            return 4;
        } @catch (NSException *exception) {
            printf("read_exception_name=%s\n", exception.name.UTF8String);
            printf("read_exception_reason=%s\n", exception.reason.UTF8String);
        } @finally {
            [handle closeFile];
        }
        NSFileHandle *safeHandle = [NSFileHandle fileHandleForReadingAtPath:path];
        NSError *error = nil;
        NSData *data = [safeHandle readDataUpToLength:9 error:&error];
        printf("replacement_returned_nil=%s error_domain=%s code=%ld\n",
               data ? "false" : "true", error.domain.UTF8String, (long)error.code);
        [safeHandle closeAndReturnError:NULL];
    }
    return 0;
}
