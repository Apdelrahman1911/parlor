// Audit-only native API witness. Not compiled into Parlor or its test source sets.
// Run only in a newly created simulator, with an isolated bundle/container.
// No Keychain writes and no credential, path, query or NSError descriptions logged.
#import <UIKit/UIKit.h>
#import <Foundation/Foundation.h>
#import <Security/Security.h>

static NSDictionary *errorCode(NSError *error) {
    return error ? @{ @"domain": error.domain, @"code": @(error.code) } : @{};
}

static NSDictionary *probe(void) {
    NSMutableDictionary *out = [NSMutableDictionary dictionary];
    NSDictionary *query = @{
        (__bridge id)kSecClass: (__bridge id)kSecClassGenericPassword,
        (__bridge id)kSecAttrService: @"com.parlor.app.resumable-session.v1",
        (__bridge id)kSecAttrAccount: @"p2p-resumable-session-v1",
        (__bridge id)kSecAttrSynchronizable: @NO,
        (__bridge id)kSecReturnData: @YES,
        (__bridge id)kSecMatchLimit: (__bridge id)kSecMatchLimitOne
    };
    CFTypeRef value = NULL;
    OSStatus status = SecItemCopyMatching((__bridge CFDictionaryRef)query, &value);
    out[@"keychain_status"] = @(status);
    out[@"keychain_result_was_null"] = @(value == NULL);
    out[@"constant_errSecItemNotFound"] = @(errSecItemNotFound);
    out[@"constant_errSecMissingEntitlement"] = @(errSecMissingEntitlement);
    if (value != NULL) CFRelease(value);

    NSFileManager *files = NSFileManager.defaultManager;
    NSError *error = nil;
    NSURL *documents = [files URLForDirectory:NSDocumentDirectory inDomain:NSUserDomainMask
                           appropriateForURL:nil create:NO error:&error];
    out[@"documents_url_available"] = @(documents != nil);
    out[@"documents_error"] = errorCode(error);
    if (documents != nil) {
        out[@"legacy_directory_exists"] = @([files fileExistsAtPath:
            [[documents URLByAppendingPathComponent:@"snapshots" isDirectory:YES] path]]);
    }
    error = nil;
    NSURL *support = [files URLForDirectory:NSApplicationSupportDirectory inDomain:NSUserDomainMask
                         appropriateForURL:nil create:YES error:&error];
    out[@"support_url_available"] = @(support != nil);
    out[@"support_error"] = errorCode(error);
    if (support != nil) {
        NSURL *directory = [support URLByAppendingPathComponent:@"Parlor/snapshots" isDirectory:YES];
        error = nil;
        BOOL created = [files createDirectoryAtPath:directory.path withIntermediateDirectories:YES
            attributes:@{NSFileProtectionKey: NSFileProtectionComplete} error:&error];
        out[@"protected_directory_created"] = @(created);
        out[@"create_error"] = errorCode(error);
        if (created) {
            error = nil;
            out[@"backup_exclusion_set"] = @([directory setResourceValue:@YES
                forKey:NSURLIsExcludedFromBackupKey error:&error]);
            out[@"backup_exclusion_error"] = errorCode(error);
            error = nil;
            NSArray *names = [files contentsOfDirectoryAtPath:directory.path error:&error];
            out[@"list_succeeded"] = @(names != nil);
            out[@"list_count"] = @(names.count);
            out[@"list_error"] = errorCode(error);
        }
    }
    return out;
}

@interface AuditDelegate : UIResponder <UIApplicationDelegate>
@property (strong, nonatomic) UIWindow *window;
@end
@implementation AuditDelegate
- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)options {
    self.window = [[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
    self.window.rootViewController = [UIViewController new];
    [self.window makeKeyAndVisible];
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_DEFAULT, 0), ^{
        NSDictionary *result = probe();
        NSData *json = [NSJSONSerialization dataWithJSONObject:result options:NSJSONWritingPrettyPrinted error:nil];
        // This is the synthetic app's own fresh container, not a Parlor/player store.
        [json writeToFile:[NSHomeDirectory() stringByAppendingPathComponent:@"Documents/probe-result.json"]
                  options:NSDataWritingAtomic error:nil];
    });
    return YES;
}
@end

int main(int argc, char *argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass(AuditDelegate.class));
    }
}
