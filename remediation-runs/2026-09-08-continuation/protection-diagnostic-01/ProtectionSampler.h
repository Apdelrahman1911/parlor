// Copy-only diagnostic. The caller, not this sampler, authorizes the exact path.
// No writes, enumeration, app identity, Keychain access, or held descriptor on return.
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN
FOUNDATION_EXPORT NSDictionary *ParlorProtectionSample(NSString *path);
FOUNDATION_EXPORT NSDictionary *ParlorProtectionImplementation(id receiver, SEL selector);
FOUNDATION_EXPORT NSDictionary *ParlorProtectionImageIdentity(const void *address);
NS_ASSUME_NONNULL_END
