#import <Foundation/NSArray.h>
#import <Foundation/NSDictionary.h>
#import <Foundation/NSError.h>
#import <Foundation/NSObject.h>
#import <Foundation/NSSet.h>
#import <Foundation/NSString.h>
#import <Foundation/NSValue.h>

@class ComposeAppIOSR1AppHostProbe, ComposeAppKoin_coreBeanDefinition<T>, ComposeAppKoin_coreCallbacks<T>, ComposeAppKoin_coreExtensionManager, ComposeAppKoin_coreInstanceFactory<T>, ComposeAppKoin_coreInstanceFactoryCompanion, ComposeAppKoin_coreInstanceRegistry, ComposeAppKoin_coreKind, ComposeAppKoin_coreKoin, ComposeAppKoin_coreKoinDefinition<R>, ComposeAppKoin_coreLevel, ComposeAppKoin_coreLockable, ComposeAppKoin_coreLogger, ComposeAppKoin_coreModule, ComposeAppKoin_coreParametersHolder, ComposeAppKoin_corePropertyRegistry, ComposeAppKoin_coreResolutionContext, ComposeAppKoin_coreScope, ComposeAppKoin_coreScopeDSL, ComposeAppKoin_coreScopeRegistry, ComposeAppKoin_coreScopeRegistryCompanion, ComposeAppKoin_coreSingleInstanceFactory<T>, ComposeAppKotlinArray<T>, ComposeAppKotlinByteArray, ComposeAppKotlinByteIterator, ComposeAppKotlinEnum<E>, ComposeAppKotlinEnumCompanion, ComposeAppKotlinException, ComposeAppKotlinIllegalStateException, ComposeAppKotlinLazyThreadSafetyMode, ComposeAppKotlinRuntimeException, ComposeAppKotlinThrowable, ComposeAppLibraryDrawableResource, ComposeAppLibraryFontResource, ComposeAppLibraryPluralStringResource, ComposeAppLibraryResource, ComposeAppLibraryResourceItem, ComposeAppLibraryStringArrayResource, ComposeAppLibraryStringResource, ComposeAppPermissionStatusDeniedActionable, ComposeAppPermissionStatusFailureUnclassified, ComposeAppPermissionStatusGrantedOperational, ComposeAppPermissionStatusNotRequired, ComposeAppPermissionStatusRequesting, ComposeAppPermissionStatusUnknown, ComposeAppRes, ComposeAppResArray, ComposeAppResDrawable, ComposeAppResFont, ComposeAppResPlurals, ComposeAppResString, UIViewController;

@protocol ComposeAppKoin_coreKoinComponent, ComposeAppKoin_coreKoinExtension, ComposeAppKoin_coreKoinScopeComponent, ComposeAppKoin_coreQualifier, ComposeAppKoin_coreScopeCallback, ComposeAppKotlinComparable, ComposeAppKotlinIterator, ComposeAppKotlinKAnnotatedElement, ComposeAppKotlinKClass, ComposeAppKotlinKClassifier, ComposeAppKotlinKDeclarationContainer, ComposeAppKotlinLazy, ComposeAppKotlinx_coroutines_coreFlow, ComposeAppKotlinx_coroutines_coreFlowCollector, ComposeAppKotlinx_coroutines_coreSharedFlow, ComposeAppKotlinx_coroutines_coreStateFlow, ComposeAppLibraryQualifier, ComposeAppPermissionStatus;

NS_ASSUME_NONNULL_BEGIN
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunknown-warning-option"
#pragma clang diagnostic ignored "-Wincompatible-property-type"
#pragma clang diagnostic ignored "-Wnullability"

#pragma push_macro("_Nullable_result")
#if !__has_feature(nullability_nullable_result)
#undef _Nullable_result
#define _Nullable_result _Nullable
#endif

__attribute__((swift_name("KotlinBase")))
@interface ComposeAppBase : NSObject
- (instancetype)init __attribute__((unavailable));
+ (instancetype)new __attribute__((unavailable));
+ (void)initialize __attribute__((objc_requires_super));
@end

@interface ComposeAppBase (ComposeAppBaseCopying) <NSCopying>
@end

__attribute__((swift_name("KotlinMutableSet")))
@interface ComposeAppMutableSet<ObjectType> : NSMutableSet<ObjectType>
@end

__attribute__((swift_name("KotlinMutableDictionary")))
@interface ComposeAppMutableDictionary<KeyType, ObjectType> : NSMutableDictionary<KeyType, ObjectType>
@end

@interface NSError (NSErrorComposeAppKotlinException)
@property (readonly) id _Nullable kotlinException;
@end

__attribute__((swift_name("KotlinNumber")))
@interface ComposeAppNumber : NSNumber
- (instancetype)initWithChar:(char)value __attribute__((unavailable));
- (instancetype)initWithUnsignedChar:(unsigned char)value __attribute__((unavailable));
- (instancetype)initWithShort:(short)value __attribute__((unavailable));
- (instancetype)initWithUnsignedShort:(unsigned short)value __attribute__((unavailable));
- (instancetype)initWithInt:(int)value __attribute__((unavailable));
- (instancetype)initWithUnsignedInt:(unsigned int)value __attribute__((unavailable));
- (instancetype)initWithLong:(long)value __attribute__((unavailable));
- (instancetype)initWithUnsignedLong:(unsigned long)value __attribute__((unavailable));
- (instancetype)initWithLongLong:(long long)value __attribute__((unavailable));
- (instancetype)initWithUnsignedLongLong:(unsigned long long)value __attribute__((unavailable));
- (instancetype)initWithFloat:(float)value __attribute__((unavailable));
- (instancetype)initWithDouble:(double)value __attribute__((unavailable));
- (instancetype)initWithBool:(BOOL)value __attribute__((unavailable));
- (instancetype)initWithInteger:(NSInteger)value __attribute__((unavailable));
- (instancetype)initWithUnsignedInteger:(NSUInteger)value __attribute__((unavailable));
+ (instancetype)numberWithChar:(char)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedChar:(unsigned char)value __attribute__((unavailable));
+ (instancetype)numberWithShort:(short)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedShort:(unsigned short)value __attribute__((unavailable));
+ (instancetype)numberWithInt:(int)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedInt:(unsigned int)value __attribute__((unavailable));
+ (instancetype)numberWithLong:(long)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedLong:(unsigned long)value __attribute__((unavailable));
+ (instancetype)numberWithLongLong:(long long)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedLongLong:(unsigned long long)value __attribute__((unavailable));
+ (instancetype)numberWithFloat:(float)value __attribute__((unavailable));
+ (instancetype)numberWithDouble:(double)value __attribute__((unavailable));
+ (instancetype)numberWithBool:(BOOL)value __attribute__((unavailable));
+ (instancetype)numberWithInteger:(NSInteger)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedInteger:(NSUInteger)value __attribute__((unavailable));
@end

__attribute__((swift_name("KotlinByte")))
@interface ComposeAppByte : ComposeAppNumber
- (instancetype)initWithChar:(char)value;
+ (instancetype)numberWithChar:(char)value;
@end

__attribute__((swift_name("KotlinUByte")))
@interface ComposeAppUByte : ComposeAppNumber
- (instancetype)initWithUnsignedChar:(unsigned char)value;
+ (instancetype)numberWithUnsignedChar:(unsigned char)value;
@end

__attribute__((swift_name("KotlinShort")))
@interface ComposeAppShort : ComposeAppNumber
- (instancetype)initWithShort:(short)value;
+ (instancetype)numberWithShort:(short)value;
@end

__attribute__((swift_name("KotlinUShort")))
@interface ComposeAppUShort : ComposeAppNumber
- (instancetype)initWithUnsignedShort:(unsigned short)value;
+ (instancetype)numberWithUnsignedShort:(unsigned short)value;
@end

__attribute__((swift_name("KotlinInt")))
@interface ComposeAppInt : ComposeAppNumber
- (instancetype)initWithInt:(int)value;
+ (instancetype)numberWithInt:(int)value;
@end

__attribute__((swift_name("KotlinUInt")))
@interface ComposeAppUInt : ComposeAppNumber
- (instancetype)initWithUnsignedInt:(unsigned int)value;
+ (instancetype)numberWithUnsignedInt:(unsigned int)value;
@end

__attribute__((swift_name("KotlinLong")))
@interface ComposeAppLong : ComposeAppNumber
- (instancetype)initWithLongLong:(long long)value;
+ (instancetype)numberWithLongLong:(long long)value;
@end

__attribute__((swift_name("KotlinULong")))
@interface ComposeAppULong : ComposeAppNumber
- (instancetype)initWithUnsignedLongLong:(unsigned long long)value;
+ (instancetype)numberWithUnsignedLongLong:(unsigned long long)value;
@end

__attribute__((swift_name("KotlinFloat")))
@interface ComposeAppFloat : ComposeAppNumber
- (instancetype)initWithFloat:(float)value;
+ (instancetype)numberWithFloat:(float)value;
@end

__attribute__((swift_name("KotlinDouble")))
@interface ComposeAppDouble : ComposeAppNumber
- (instancetype)initWithDouble:(double)value;
+ (instancetype)numberWithDouble:(double)value;
@end

__attribute__((swift_name("KotlinBoolean")))
@interface ComposeAppBoolean : ComposeAppNumber
- (instancetype)initWithBool:(BOOL)value;
+ (instancetype)numberWithBool:(BOOL)value;
@end


/**
 * One probe per app process; start/cancel Swift names require generated-header verification.
 * The original MainViewController/App must already have started the real Koin graph.
 */
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("IOSR1AppHostProbe")))
@interface ComposeAppIOSR1AppHostProbe : ComposeAppBase
+ (instancetype)alloc __attribute__((unavailable));

/**
 * One probe per app process; start/cancel Swift names require generated-header verification.
 * The original MainViewController/App must already have started the real Koin graph.
 */
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)iOSR1AppHostProbe __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppIOSR1AppHostProbe *shared __attribute__((swift_name("shared")));
- (void)cancel __attribute__((swift_name("cancel()")));
- (void)startOnJson:(void (^)(NSString *))onJson __attribute__((swift_name("start(onJson:)")));
@end


/**
 * Platform boundary for the runtime permissions required by Party Play.
 *
 * Parlor's shipped transport is NSD/JmDNS plus TCP. It does not provision a
 * Wi-Fi network, so it requests no dangerous Android Nearby/Location
 * permission. The gate remains a platform seam for a future transport that
 * does require one, and exposes status, a request entry point, and an
 * `openAppSettings()` fallback. Apple Local Network access is deliberately
 * modeled from real transport evidence because iOS has no truthful preflight
 * API; an empty discovery result must never be called a proven denial.
 */
__attribute__((swift_name("P2pPermissionGate")))
@protocol ComposeAppP2pPermissionGate
@required

/**
 * Opens the OS app-settings screen so the user can flip the permission
 * back on after it was permanently denied. No-op on platforms that have
 * no permission gate.
 */
- (void)openAppSettings __attribute__((swift_name("openAppSettings()")));

/**
 * Triggers the platform's permission prompt. Suspends until the user
 * answers. On iOS this only records that the player chose to continue;
 * the system prompt appears when the following transport operation first
 * advertises or browses.
 *
 * @note This method converts instances of CancellationException to errors.
 * Other uncaught Kotlin exceptions are fatal.
*/
- (void)requestWithCompletionHandler:(void (^)(id<ComposeAppPermissionStatus> _Nullable, NSError * _Nullable))completionHandler __attribute__((swift_name("request(completionHandler:)")));

/** Whether this platform exposes a relevant per-app network setting. */
@property (readonly) BOOL canOpenNetworkSettings __attribute__((swift_name("canOpenNetworkSettings")));
@property (readonly) id<ComposeAppKotlinx_coroutines_coreStateFlow> status __attribute__((swift_name("status")));
@end

__attribute__((swift_name("PermissionStatus")))
@protocol ComposeAppPermissionStatus
@required
@end


/** A stable platform/API signal proved that Settings action is required. */
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("PermissionStatusDeniedActionable")))
@interface ComposeAppPermissionStatusDeniedActionable : ComposeAppBase <ComposeAppPermissionStatus>
+ (instancetype)alloc __attribute__((unavailable));

/** A stable platform/API signal proved that Settings action is required. */
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)deniedActionable __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppPermissionStatusDeniedActionable *shared __attribute__((swift_name("shared")));

/** A stable platform/API signal proved that Settings action is required. */
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));

/** A stable platform/API signal proved that Settings action is required. */
- (NSUInteger)hash __attribute__((swift_name("hash()")));

/** A stable platform/API signal proved that Settings action is required. */
- (NSString *)description __attribute__((swift_name("description()")));
@end


/** Failure was real, but could not truthfully be classified as denial. */
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("PermissionStatusFailureUnclassified")))
@interface ComposeAppPermissionStatusFailureUnclassified : ComposeAppBase <ComposeAppPermissionStatus>
+ (instancetype)alloc __attribute__((unavailable));

/** Failure was real, but could not truthfully be classified as denial. */
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)failureUnclassified __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppPermissionStatusFailureUnclassified *shared __attribute__((swift_name("shared")));

/** Failure was real, but could not truthfully be classified as denial. */
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));

/** Failure was real, but could not truthfully be classified as denial. */
- (NSUInteger)hash __attribute__((swift_name("hash()")));

/** Failure was real, but could not truthfully be classified as denial. */
- (NSString *)description __attribute__((swift_name("description()")));
@end


/** A real advertise or authenticated-connect operation has succeeded. */
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("PermissionStatusGrantedOperational")))
@interface ComposeAppPermissionStatusGrantedOperational : ComposeAppBase <ComposeAppPermissionStatus>
+ (instancetype)alloc __attribute__((unavailable));

/** A real advertise or authenticated-connect operation has succeeded. */
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)grantedOperational __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppPermissionStatusGrantedOperational *shared __attribute__((swift_name("shared")));

/** A real advertise or authenticated-connect operation has succeeded. */
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));

/** A real advertise or authenticated-connect operation has succeeded. */
- (NSUInteger)hash __attribute__((swift_name("hash()")));

/** A real advertise or authenticated-connect operation has succeeded. */
- (NSString *)description __attribute__((swift_name("description()")));
@end


/** This platform requires no runtime permission for Parlor's base LAN. */
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("PermissionStatusNotRequired")))
@interface ComposeAppPermissionStatusNotRequired : ComposeAppBase <ComposeAppPermissionStatus>
+ (instancetype)alloc __attribute__((unavailable));

/** This platform requires no runtime permission for Parlor's base LAN. */
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)notRequired __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppPermissionStatusNotRequired *shared __attribute__((swift_name("shared")));

/** This platform requires no runtime permission for Parlor's base LAN. */
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));

/** This platform requires no runtime permission for Parlor's base LAN. */
- (NSUInteger)hash __attribute__((swift_name("hash()")));

/** This platform requires no runtime permission for Parlor's base LAN. */
- (NSString *)description __attribute__((swift_name("description()")));
@end


/** The transport is currently attempting to establish LAN operation. */
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("PermissionStatusRequesting")))
@interface ComposeAppPermissionStatusRequesting : ComposeAppBase <ComposeAppPermissionStatus>
+ (instancetype)alloc __attribute__((unavailable));

/** The transport is currently attempting to establish LAN operation. */
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)requesting __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppPermissionStatusRequesting *shared __attribute__((swift_name("shared")));

/** The transport is currently attempting to establish LAN operation. */
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));

/** The transport is currently attempting to establish LAN operation. */
- (NSUInteger)hash __attribute__((swift_name("hash()")));

/** The transport is currently attempting to establish LAN operation. */
- (NSString *)description __attribute__((swift_name("description()")));
@end


/** Apple access is unknown until a real LAN operation is attempted. */
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("PermissionStatusUnknown")))
@interface ComposeAppPermissionStatusUnknown : ComposeAppBase <ComposeAppPermissionStatus>
+ (instancetype)alloc __attribute__((unavailable));

/** Apple access is unknown until a real LAN operation is attempted. */
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)unknown __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppPermissionStatusUnknown *shared __attribute__((swift_name("shared")));

/** Apple access is unknown until a real LAN operation is attempted. */
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));

/** Apple access is unknown until a real LAN operation is attempted. */
- (NSUInteger)hash __attribute__((swift_name("hash()")));

/** Apple access is unknown until a real LAN operation is attempted. */
- (NSString *)description __attribute__((swift_name("description()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Res")))
@interface ComposeAppRes : ComposeAppBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)res __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppRes *shared __attribute__((swift_name("shared")));

/**
 * Returns the URI string of the resource file at the specified path.
 *
 * Example: `val uri = Res.getUri("files/key.bin")`
 *
 * @param path The path of the file in the compose resource's directory.
 * @return The URI string of the file.
 */
- (NSString *)getUriPath:(NSString *)path __attribute__((swift_name("getUri(path:)")));

/**
 * Reads the content of the resource file at the specified path and returns it as a byte array.
 *
 * Example: `val bytes = Res.readBytes("files/key.bin")`
 *
 * @param path The path of the file to read in the compose resource's directory.
 * @return The content of the file as a byte array.
 *
 * @note This method converts instances of CancellationException to errors.
 * Other uncaught Kotlin exceptions are fatal.
*/
- (void)readBytesPath:(NSString *)path completionHandler:(void (^)(ComposeAppKotlinByteArray * _Nullable, NSError * _Nullable))completionHandler __attribute__((swift_name("readBytes(path:completionHandler:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Res.array")))
@interface ComposeAppResArray : ComposeAppBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)array __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppResArray *shared __attribute__((swift_name("shared")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Res.drawable")))
@interface ComposeAppResDrawable : ComposeAppBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)drawable __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppResDrawable *shared __attribute__((swift_name("shared")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Res.font")))
@interface ComposeAppResFont : ComposeAppBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)font __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppResFont *shared __attribute__((swift_name("shared")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Res.plurals")))
@interface ComposeAppResPlurals : ComposeAppBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)plurals __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppResPlurals *shared __attribute__((swift_name("shared")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Res.string")))
@interface ComposeAppResString : ComposeAppBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)string __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppResString *shared __attribute__((swift_name("shared")));
@end

@interface ComposeAppRes (Extensions)
@property (readonly) NSDictionary<NSString *, ComposeAppLibraryDrawableResource *> *allDrawableResources __attribute__((swift_name("allDrawableResources")));
@property (readonly) NSDictionary<NSString *, ComposeAppLibraryFontResource *> *allFontResources __attribute__((swift_name("allFontResources")));
@property (readonly) NSDictionary<NSString *, ComposeAppLibraryPluralStringResource *> *allPluralStringResources __attribute__((swift_name("allPluralStringResources")));
@property (readonly) NSDictionary<NSString *, ComposeAppLibraryStringArrayResource *> *allStringArrayResources __attribute__((swift_name("allStringArrayResources")));
@property (readonly) NSDictionary<NSString *, ComposeAppLibraryStringResource *> *allStringResources __attribute__((swift_name("allStringResources")));
@end

@interface ComposeAppResString (Extensions)
@property (readonly) ComposeAppLibraryStringResource *app_name __attribute__((swift_name("app_name")));
@property (readonly) ComposeAppLibraryStringResource *home_continue_label __attribute__((swift_name("home_continue_label")));
@property (readonly) ComposeAppLibraryStringResource *home_eyebrow __attribute__((swift_name("home_eyebrow")));
@property (readonly) ComposeAppLibraryStringResource *home_game_kicker_format __attribute__((swift_name("home_game_kicker_format")));
@property (readonly) ComposeAppLibraryStringResource *home_games_count __attribute__((swift_name("home_games_count")));
@property (readonly) ComposeAppLibraryStringResource *home_games_label __attribute__((swift_name("home_games_label")));
@property (readonly) ComposeAppLibraryStringResource *home_local_lan_meta __attribute__((swift_name("home_local_lan_meta")));
@property (readonly) ComposeAppLibraryStringResource *home_local_meta __attribute__((swift_name("home_local_meta")));
@property (readonly) ComposeAppLibraryStringResource *home_mafia_kicker __attribute__((swift_name("home_mafia_kicker")));
@property (readonly) ComposeAppLibraryStringResource *home_mafia_open __attribute__((swift_name("home_mafia_open")));
@property (readonly) ComposeAppLibraryStringResource *home_mafia_open_description __attribute__((swift_name("home_mafia_open_description")));
@property (readonly) ComposeAppLibraryStringResource *home_mafia_subtitle __attribute__((swift_name("home_mafia_subtitle")));
@property (readonly) ComposeAppLibraryStringResource *home_mafia_tagline __attribute__((swift_name("home_mafia_tagline")));
@property (readonly) ComposeAppLibraryStringResource *home_mafia_title __attribute__((swift_name("home_mafia_title")));
@property (readonly) ComposeAppLibraryStringResource *home_players_exact_format __attribute__((swift_name("home_players_exact_format")));
@property (readonly) ComposeAppLibraryStringResource *home_players_range_format __attribute__((swift_name("home_players_range_format")));
@property (readonly) ComposeAppLibraryStringResource *home_recovery_checking __attribute__((swift_name("home_recovery_checking")));
@property (readonly) ComposeAppLibraryStringResource *home_recovery_retry __attribute__((swift_name("home_recovery_retry")));
@property (readonly) ComposeAppLibraryStringResource *home_recovery_retry_description __attribute__((swift_name("home_recovery_retry_description")));
@property (readonly) ComposeAppLibraryStringResource *home_recovery_unavailable_body __attribute__((swift_name("home_recovery_unavailable_body")));
@property (readonly) ComposeAppLibraryStringResource *home_recovery_unavailable_title __attribute__((swift_name("home_recovery_unavailable_title")));
@property (readonly) ComposeAppLibraryStringResource *home_resume_multiplayer_description __attribute__((swift_name("home_resume_multiplayer_description")));
@property (readonly) ComposeAppLibraryStringResource *home_resume_multiplayer_subtitle __attribute__((swift_name("home_resume_multiplayer_subtitle")));
@property (readonly) ComposeAppLibraryStringResource *home_resume_multiplayer_title __attribute__((swift_name("home_resume_multiplayer_title")));
@property (readonly) ComposeAppLibraryStringResource *home_resume_open_failed __attribute__((swift_name("home_resume_open_failed")));
@property (readonly) ComposeAppLibraryStringResource *home_resume_tile_description __attribute__((swift_name("home_resume_tile_description")));
@property (readonly) ComposeAppLibraryStringResource *home_resume_tile_game_description __attribute__((swift_name("home_resume_tile_game_description")));
@property (readonly) ComposeAppLibraryStringResource *home_resume_tile_game_title __attribute__((swift_name("home_resume_tile_game_title")));
@property (readonly) ComposeAppLibraryStringResource *home_resume_tile_position __attribute__((swift_name("home_resume_tile_position")));
@property (readonly) ComposeAppLibraryStringResource *home_resume_tile_subtitle __attribute__((swift_name("home_resume_tile_subtitle")));
@property (readonly) ComposeAppLibraryStringResource *home_resume_tile_title __attribute__((swift_name("home_resume_tile_title")));
@property (readonly) ComposeAppLibraryStringResource *home_resume_tile_unknown_description __attribute__((swift_name("home_resume_tile_unknown_description")));
@property (readonly) ComposeAppLibraryStringResource *home_saved_on_device __attribute__((swift_name("home_saved_on_device")));
@property (readonly) ComposeAppLibraryStringResource *home_subtitle __attribute__((swift_name("home_subtitle")));
@property (readonly) ComposeAppLibraryStringResource *home_title __attribute__((swift_name("home_title")));
@property (readonly) ComposeAppLibraryStringResource *home_whodunit_kicker __attribute__((swift_name("home_whodunit_kicker")));
@property (readonly) ComposeAppLibraryStringResource *home_whodunit_open __attribute__((swift_name("home_whodunit_open")));
@property (readonly) ComposeAppLibraryStringResource *home_whodunit_open_description __attribute__((swift_name("home_whodunit_open_description")));
@property (readonly) ComposeAppLibraryStringResource *home_whodunit_subtitle __attribute__((swift_name("home_whodunit_subtitle")));
@property (readonly) ComposeAppLibraryStringResource *home_whodunit_tagline __attribute__((swift_name("home_whodunit_tagline")));
@property (readonly) ComposeAppLibraryStringResource *home_whodunit_title __attribute__((swift_name("home_whodunit_title")));
@property (readonly) ComposeAppLibraryStringResource *join_cancel __attribute__((swift_name("join_cancel")));
@property (readonly) ComposeAppLibraryStringResource *join_cancel_description __attribute__((swift_name("join_cancel_description")));
@property (readonly) ComposeAppLibraryStringResource *join_code_field __attribute__((swift_name("join_code_field")));
@property (readonly) ComposeAppLibraryStringResource *join_code_help __attribute__((swift_name("join_code_help")));
@property (readonly) ComposeAppLibraryStringResource *join_confirm __attribute__((swift_name("join_confirm")));
@property (readonly) ComposeAppLibraryStringResource *join_confirm_description __attribute__((swift_name("join_confirm_description")));
@property (readonly) ComposeAppLibraryStringResource *join_eyebrow __attribute__((swift_name("join_eyebrow")));
@property (readonly) ComposeAppLibraryStringResource *join_local_body __attribute__((swift_name("join_local_body")));
@property (readonly) ComposeAppLibraryStringResource *join_local_label __attribute__((swift_name("join_local_label")));
@property (readonly) ComposeAppLibraryStringResource *join_title __attribute__((swift_name("join_title")));
@property (readonly) ComposeAppLibraryStringResource *local_resume_failure_back __attribute__((swift_name("local_resume_failure_back")));
@property (readonly) ComposeAppLibraryStringResource *local_resume_failure_back_description __attribute__((swift_name("local_resume_failure_back_description")));
@property (readonly) ComposeAppLibraryStringResource *local_resume_failure_body __attribute__((swift_name("local_resume_failure_body")));
@property (readonly) ComposeAppLibraryStringResource *local_resume_failure_discard __attribute__((swift_name("local_resume_failure_discard")));
@property (readonly) ComposeAppLibraryStringResource *local_resume_failure_discard_description __attribute__((swift_name("local_resume_failure_discard_description")));
@property (readonly) ComposeAppLibraryStringResource *local_resume_failure_discard_failed __attribute__((swift_name("local_resume_failure_discard_failed")));
@property (readonly) ComposeAppLibraryStringResource *local_resume_failure_eyebrow __attribute__((swift_name("local_resume_failure_eyebrow")));
@property (readonly) ComposeAppLibraryStringResource *local_resume_failure_retry __attribute__((swift_name("local_resume_failure_retry")));
@property (readonly) ComposeAppLibraryStringResource *local_resume_failure_retry_description __attribute__((swift_name("local_resume_failure_retry_description")));
@property (readonly) ComposeAppLibraryStringResource *local_resume_failure_title __attribute__((swift_name("local_resume_failure_title")));
@property (readonly) ComposeAppLibraryStringResource *name_back __attribute__((swift_name("name_back")));
@property (readonly) ComposeAppLibraryStringResource *name_back_description __attribute__((swift_name("name_back_description")));
@property (readonly) ComposeAppLibraryStringResource *name_confirm_host __attribute__((swift_name("name_confirm_host")));
@property (readonly) ComposeAppLibraryStringResource *name_confirm_host_description __attribute__((swift_name("name_confirm_host_description")));
@property (readonly) ComposeAppLibraryStringResource *name_confirm_peer __attribute__((swift_name("name_confirm_peer")));
@property (readonly) ComposeAppLibraryStringResource *name_confirm_peer_description __attribute__((swift_name("name_confirm_peer_description")));
@property (readonly) ComposeAppLibraryStringResource *name_eyebrow_host __attribute__((swift_name("name_eyebrow_host")));
@property (readonly) ComposeAppLibraryStringResource *name_eyebrow_peer __attribute__((swift_name("name_eyebrow_peer")));
@property (readonly) ComposeAppLibraryStringResource *name_field __attribute__((swift_name("name_field")));
@property (readonly) ComposeAppLibraryStringResource *name_help __attribute__((swift_name("name_help")));
@property (readonly) ComposeAppLibraryStringResource *name_title_host __attribute__((swift_name("name_title_host")));
@property (readonly) ComposeAppLibraryStringResource *name_title_peer __attribute__((swift_name("name_title_peer")));
@property (readonly) ComposeAppLibraryStringResource *navigation_games_description __attribute__((swift_name("navigation_games_description")));
@property (readonly) ComposeAppLibraryStringResource *navigation_games_label __attribute__((swift_name("navigation_games_label")));
@property (readonly) ComposeAppLibraryStringResource *navigation_settings_description __attribute__((swift_name("navigation_settings_description")));
@property (readonly) ComposeAppLibraryStringResource *navigation_settings_label __attribute__((swift_name("navigation_settings_label")));
@property (readonly) ComposeAppLibraryStringResource *permission_back __attribute__((swift_name("permission_back")));
@property (readonly) ComposeAppLibraryStringResource *permission_back_description __attribute__((swift_name("permission_back_description")));
@property (readonly) ComposeAppLibraryStringResource *permission_body __attribute__((swift_name("permission_body")));
@property (readonly) ComposeAppLibraryStringResource *permission_continue __attribute__((swift_name("permission_continue")));
@property (readonly) ComposeAppLibraryStringResource *permission_continue_description __attribute__((swift_name("permission_continue_description")));
@property (readonly) ComposeAppLibraryStringResource *permission_denied_body __attribute__((swift_name("permission_denied_body")));
@property (readonly) ComposeAppLibraryStringResource *permission_denied_eyebrow __attribute__((swift_name("permission_denied_eyebrow")));
@property (readonly) ComposeAppLibraryStringResource *permission_eyebrow __attribute__((swift_name("permission_eyebrow")));
@property (readonly) ComposeAppLibraryStringResource *permission_open_settings __attribute__((swift_name("permission_open_settings")));
@property (readonly) ComposeAppLibraryStringResource *permission_open_settings_description __attribute__((swift_name("permission_open_settings_description")));
@property (readonly) ComposeAppLibraryStringResource *permission_permanently_denied_body __attribute__((swift_name("permission_permanently_denied_body")));
@property (readonly) ComposeAppLibraryStringResource *permission_permanently_denied_eyebrow __attribute__((swift_name("permission_permanently_denied_eyebrow")));
@property (readonly) ComposeAppLibraryStringResource *permission_title __attribute__((swift_name("permission_title")));
@property (readonly) ComposeAppLibraryStringResource *playmode_passandplay_body __attribute__((swift_name("playmode_passandplay_body")));
@property (readonly) ComposeAppLibraryStringResource *playmode_passandplay_choose __attribute__((swift_name("playmode_passandplay_choose")));
@property (readonly) ComposeAppLibraryStringResource *playmode_passandplay_choose_description __attribute__((swift_name("playmode_passandplay_choose_description")));
@property (readonly) ComposeAppLibraryStringResource *playmode_passandplay_meta __attribute__((swift_name("playmode_passandplay_meta")));
@property (readonly) ComposeAppLibraryStringResource *playmode_passandplay_meta_exact __attribute__((swift_name("playmode_passandplay_meta_exact")));
@property (readonly) ComposeAppLibraryStringResource *playmode_passandplay_title __attribute__((swift_name("playmode_passandplay_title")));
@property (readonly) ComposeAppLibraryStringResource *playmode_solo_body __attribute__((swift_name("playmode_solo_body")));
@property (readonly) ComposeAppLibraryStringResource *playmode_solo_choose __attribute__((swift_name("playmode_solo_choose")));
@property (readonly) ComposeAppLibraryStringResource *playmode_solo_choose_description __attribute__((swift_name("playmode_solo_choose_description")));
@property (readonly) ComposeAppLibraryStringResource *playmode_solo_meta __attribute__((swift_name("playmode_solo_meta")));
@property (readonly) ComposeAppLibraryStringResource *playmode_solo_title __attribute__((swift_name("playmode_solo_title")));
@property (readonly) ComposeAppLibraryStringResource *settings_appearance_dark __attribute__((swift_name("settings_appearance_dark")));
@property (readonly) ComposeAppLibraryStringResource *settings_appearance_label __attribute__((swift_name("settings_appearance_label")));
@property (readonly) ComposeAppLibraryStringResource *settings_appearance_light __attribute__((swift_name("settings_appearance_light")));
@property (readonly) ComposeAppLibraryStringResource *settings_appearance_system __attribute__((swift_name("settings_appearance_system")));
@property (readonly) ComposeAppLibraryStringResource *settings_experience_label __attribute__((swift_name("settings_experience_label")));
@property (readonly) ComposeAppLibraryStringResource *settings_eyebrow __attribute__((swift_name("settings_eyebrow")));
@property (readonly) ComposeAppLibraryStringResource *settings_language_arabic __attribute__((swift_name("settings_language_arabic")));
@property (readonly) ComposeAppLibraryStringResource *settings_language_english __attribute__((swift_name("settings_language_english")));
@property (readonly) ComposeAppLibraryStringResource *settings_language_label __attribute__((swift_name("settings_language_label")));
@property (readonly) ComposeAppLibraryStringResource *settings_language_system __attribute__((swift_name("settings_language_system")));
@property (readonly) ComposeAppLibraryStringResource *settings_reduced_motion_description __attribute__((swift_name("settings_reduced_motion_description")));
@property (readonly) ComposeAppLibraryStringResource *settings_reduced_motion_title __attribute__((swift_name("settings_reduced_motion_title")));
@property (readonly) ComposeAppLibraryStringResource *settings_save_failed __attribute__((swift_name("settings_save_failed")));
@property (readonly) ComposeAppLibraryStringResource *settings_subtitle __attribute__((swift_name("settings_subtitle")));
@property (readonly) ComposeAppLibraryStringResource *settings_title __attribute__((swift_name("settings_title")));
@property (readonly) ComposeAppLibraryStringResource *setup_back_description __attribute__((swift_name("setup_back_description")));
@property (readonly) ComposeAppLibraryStringResource *setup_each_device_label __attribute__((swift_name("setup_each_device_label")));
@property (readonly) ComposeAppLibraryStringResource *setup_eyebrow __attribute__((swift_name("setup_eyebrow")));
@property (readonly) ComposeAppLibraryStringResource *setup_host_body __attribute__((swift_name("setup_host_body")));
@property (readonly) ComposeAppLibraryStringResource *setup_host_choose __attribute__((swift_name("setup_host_choose")));
@property (readonly) ComposeAppLibraryStringResource *setup_host_choose_description __attribute__((swift_name("setup_host_choose_description")));
@property (readonly) ComposeAppLibraryStringResource *setup_host_meta __attribute__((swift_name("setup_host_meta")));
@property (readonly) ComposeAppLibraryStringResource *setup_host_title __attribute__((swift_name("setup_host_title")));
@property (readonly) ComposeAppLibraryStringResource *setup_join_body __attribute__((swift_name("setup_join_body")));
@property (readonly) ComposeAppLibraryStringResource *setup_join_choose __attribute__((swift_name("setup_join_choose")));
@property (readonly) ComposeAppLibraryStringResource *setup_join_choose_description __attribute__((swift_name("setup_join_choose_description")));
@property (readonly) ComposeAppLibraryStringResource *setup_join_meta __attribute__((swift_name("setup_join_meta")));
@property (readonly) ComposeAppLibraryStringResource *setup_join_title __attribute__((swift_name("setup_join_title")));
@property (readonly) ComposeAppLibraryStringResource *setup_lan_note __attribute__((swift_name("setup_lan_note")));
@property (readonly) ComposeAppLibraryStringResource *setup_mode_unavailable __attribute__((swift_name("setup_mode_unavailable")));
@property (readonly) ComposeAppLibraryStringResource *setup_multiplayer_disabled __attribute__((swift_name("setup_multiplayer_disabled")));
@property (readonly) ComposeAppLibraryStringResource *setup_one_device_label __attribute__((swift_name("setup_one_device_label")));
@property (readonly) ComposeAppLibraryStringResource *setup_solo_requires_exact __attribute__((swift_name("setup_solo_requires_exact")));
@property (readonly) ComposeAppLibraryStringResource *setup_solo_requires_range __attribute__((swift_name("setup_solo_requires_range")));
@property (readonly) ComposeAppLibraryStringResource *setup_solo_unavailable_description_format __attribute__((swift_name("setup_solo_unavailable_description_format")));
@property (readonly) ComposeAppLibraryStringResource *setup_solo_unavailable_title __attribute__((swift_name("setup_solo_unavailable_title")));
@property (readonly) ComposeAppLibraryStringResource *setup_subtitle __attribute__((swift_name("setup_subtitle")));
@property (readonly) ComposeAppLibraryStringResource *setup_title __attribute__((swift_name("setup_title")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("AppModuleKt")))
@interface ComposeAppAppModuleKt : ComposeAppBase
@property (class, readonly) NSArray<ComposeAppKoin_coreModule *> *allModules __attribute__((swift_name("allModules")));

/**
 * Root Koin assembly for the Parlor shell. Each game module contributes its
 * own Koin module via the list below.
 *
 * Platform-specific actuals (snapshot/settings/credential backing and P2P kit
 * bootstrap) are provided by their respective Android, iOS, and Desktop Koin
 * modules.
 *
 * Persistent settings and snapshot files are bound by
 * [platformStorageModule].
 */
@property (class, readonly) ComposeAppKoin_coreModule *coreModule __attribute__((swift_name("coreModule")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("ContentModuleKt")))
@interface ComposeAppContentModuleKt : ComposeAppBase

/**
 * Bundled/offline content pipeline for the first production release.
 *
 * Wires:
 *  - the installed game registry;
 *  - an explicit unavailable remote source (no synthetic backend);
 *  - an in-memory cache;
 *  - the Whodunit module's bundled case source;
 *  - strict envelope and per-game payload validation;
 *  - the existing cache/remote/bundled repository contract.
 *
 * The repository sees the remote source as unavailable and therefore loads
 * the bundled case through the same validator used by any future HTTPS
 * source. This accurately represents the release's offline-only capability
 * and keeps Ktor's MockEngine out of production binaries.
 */
@property (class, readonly) ComposeAppKoin_coreModule *contentModule __attribute__((swift_name("contentModule")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("MainViewControllerKt")))
@interface ComposeAppMainViewControllerKt : ComposeAppBase

/**
 * iOS Compose Multiplatform entry. The Xcode wrapper (iosApp project) calls
 * this from SwiftUI / AppDelegate to host the Compose root view.
 *
 * The Koin start guard prevents a re-start if the iOS lifecycle invokes us
 * twice (e.g., on hot-reload).
 */
+ (UIViewController *)MainViewController __attribute__((swift_name("MainViewController()")));

/** SwiftUI scenePhase bridge; lifecycle policy remains common Kotlin code. */
+ (void)NotifyAppBackgrounded __attribute__((swift_name("NotifyAppBackgrounded()")));
+ (void)NotifyAppForegrounded __attribute__((swift_name("NotifyAppForegrounded()")));

/** Covers private UI without suspending the still-foreground LAN session. */
+ (void)NotifyAppInactive __attribute__((swift_name("NotifyAppInactive()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("P2pPermissionGateKt")))
@interface ComposeAppP2pPermissionGateKt : ComposeAppBase
+ (BOOL)entersMultiplayerWithoutRationale:(id<ComposeAppPermissionStatus>)receiver __attribute__((swift_name("entersMultiplayerWithoutRationale(_:)")));
+ (BOOL)mayAttemptNetwork:(id<ComposeAppPermissionStatus>)receiver __attribute__((swift_name("mayAttemptNetwork(_:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("PlatformStorage_iosKt")))
@interface ComposeAppPlatformStorage_iosKt : ComposeAppBase
+ (ComposeAppKoin_coreModule *)platformStorageModule __attribute__((swift_name("platformStorageModule()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("StorageModuleKt")))
@interface ComposeAppStorageModuleKt : ComposeAppBase

/**
 * Shared snapshot-store binding.
 *
 * The platform module supplies the protected file-system implementation and
 * [coreModule] supplies the strict [Json] instance. Game-owned snapshot codecs
 * and writers validate and persist through this common [SnapshotStore].
 */
@property (class, readonly) ComposeAppKoin_coreModule *storageModule __attribute__((swift_name("storageModule")));
@end

__attribute__((swift_name("KotlinThrowable")))
@interface ComposeAppKotlinThrowable : ComposeAppBase
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(ComposeAppKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(ComposeAppKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));

/**
 * @note annotations
 *   kotlin.experimental.ExperimentalNativeApi
*/
- (ComposeAppKotlinArray<NSString *> *)getStackTrace __attribute__((swift_name("getStackTrace()")));
- (void)printStackTrace __attribute__((swift_name("printStackTrace()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) ComposeAppKotlinThrowable * _Nullable cause __attribute__((swift_name("cause")));
@property (readonly) NSString * _Nullable message __attribute__((swift_name("message")));
- (NSError *)asError __attribute__((swift_name("asError()")));
@end

__attribute__((swift_name("KotlinException")))
@interface ComposeAppKotlinException : ComposeAppKotlinThrowable
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(ComposeAppKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(ComposeAppKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end

__attribute__((swift_name("KotlinRuntimeException")))
@interface ComposeAppKotlinRuntimeException : ComposeAppKotlinException
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(ComposeAppKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(ComposeAppKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end

__attribute__((swift_name("KotlinIllegalStateException")))
@interface ComposeAppKotlinIllegalStateException : ComposeAppKotlinRuntimeException
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(ComposeAppKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(ComposeAppKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end


/**
 * @note annotations
 *   kotlin.SinceKotlin(version="1.4")
*/
__attribute__((swift_name("KotlinCancellationException")))
@interface ComposeAppKotlinCancellationException : ComposeAppKotlinIllegalStateException
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(ComposeAppKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(ComposeAppKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end


/**
 * An asynchronous data stream that sequentially emits values and completes normally or with an exception.
 *
 * _Intermediate operators_ on the flow such as [map], [filter], [take], [zip], etc are functions that are
 * applied to the _upstream_ flow or flows and return a _downstream_ flow where further operators can be applied to.
 * Intermediate operations do not execute any code in the flow and are not suspending functions themselves.
 * They only set up a chain of operations for future execution and quickly return.
 * This is known as a _cold flow_ property.
 *
 * _Terminal operators_ on the flow are either suspending functions such as [collect], [single], [reduce], [toList], etc.
 * or [launchIn] operator that starts collection of the flow in the given scope.
 * They are applied to the upstream flow and trigger execution of all operations.
 * Execution of the flow is also called _collecting the flow_  and is always performed in a suspending manner
 * without actual blocking. Terminal operators complete normally or exceptionally depending on successful or failed
 * execution of all the flow operations in the upstream. The most basic terminal operator is [collect], for example:
 *
 * ```
 * try {
 *     flow.collect { value ->
 *         println("Received $value")
 *     }
 * } catch (e: Exception) {
 *     println("The flow has thrown an exception: $e")
 * }
 * ```
 *
 * By default, flows are _sequential_ and all flow operations are executed sequentially in the same coroutine,
 * with an exception for a few operations specifically designed to introduce concurrency into flow
 * execution such as [buffer] and [flatMapMerge]. See their documentation for details.
 *
 * The `Flow` interface does not carry information whether a flow is a _cold_ stream that can be collected repeatedly and
 * triggers execution of the same code every time it is collected, or if it is a _hot_ stream that emits different
 * values from the same running source on each collection. Usually flows represent _cold_ streams, but
 * there is a [SharedFlow] subtype that represents _hot_ streams. In addition to that, any flow can be turned
 * into a _hot_ one by the [stateIn] and [shareIn] operators, or by converting the flow into a hot channel
 * via the [produceIn] operator.
 *
 * ### Flow builders
 *
 * There are the following basic ways to create a flow:
 *
 * - [flowOf(...)][flowOf] functions to create a flow from a fixed set of values.
 * - [asFlow()][asFlow] extension functions on various types to convert them into flows.
 * - [flow { ... }][flow] builder function to construct arbitrary flows from
 *   sequential calls to [emit][FlowCollector.emit] function.
 * - [channelFlow { ... }][channelFlow] builder function to construct arbitrary flows from
 *   potentially concurrent calls to the [send][kotlinx.coroutines.channels.SendChannel.send] function.
 * - [MutableStateFlow] and [MutableSharedFlow] define the corresponding constructor functions to create
 *   a _hot_ flow that can be directly updated.
 *
 * ### Flow constraints
 *
 * All implementations of the `Flow` interface must adhere to two key properties described in detail below:
 *
 * - Context preservation.
 * - Exception transparency.
 *
 * These properties ensure the ability to perform local reasoning about the code with flows and modularize the code
 * in such a way that upstream flow emitters can be developed separately from downstream flow collectors.
 * A user of a flow does not need to be aware of implementation details of the upstream flows it uses.
 *
 * ### Context preservation
 *
 * The flow has a context preservation property: it encapsulates its own execution context and never propagates or leaks
 * it downstream, thus making reasoning about the execution context of particular transformations or terminal
 * operations trivial.
 *
 * There is only one way to change the context of a flow: the [flowOn][Flow.flowOn] operator
 * that changes the upstream context ("everything above the `flowOn` operator").
 * For additional information refer to its documentation.
 *
 * This reasoning can be demonstrated in practice:
 *
 * ```
 * val flowA = flowOf(1, 2, 3)
 *     .map { it + 1 } // Will be executed in ctxA
 *     .flowOn(ctxA) // Changes the upstream context: flowOf and map
 *
 * // Now we have a context-preserving flow: it is executed somewhere but this information is encapsulated in the flow itself
 *
 * val filtered = flowA // ctxA is encapsulated in flowA
 *    .filter { it == 3 } // Pure operator without a context yet
 *
 * withContext(Dispatchers.Main) {
 *     // All non-encapsulated operators will be executed in Main: filter and single
 *     val result = filtered.single()
 *     myUi.text = result
 * }
 * ```
 *
 * From the implementation point of view, it means that all flow implementations should
 * only emit from the same coroutine context.
 * This constraint is efficiently enforced by the default [flow] builder.
 * The [flow] builder should be used if the flow implementation does not start any coroutines.
 * Its implementation prevents most of the development mistakes:
 *
 * ```
 * val myFlow = flow {
 *     // GlobalScope.launch { // is prohibited
 *     // launch(Dispatchers.IO) { // is prohibited
 *     // withContext(CoroutineName("myFlow")) { // is prohibited
 *     emit(1) // OK
 *     coroutineScope {
 *         emit(2) // OK -- still the same coroutine
 *     }
 * }
 * ```
 *
 * Use [channelFlow] if the collection and emission of a flow are to be separated into multiple coroutines.
 * It encapsulates all the context preservation work and allows you to focus on your
 * domain-specific problem, rather than invariant implementation details.
 * It is possible to use any combination of coroutine builders from within [channelFlow].
 *
 * If you are looking for performance and are sure that no concurrent emits and context jumps will happen,
 * the [flow] builder can be used alongside a [coroutineScope] or [supervisorScope] instead:
 * - Scoped primitive should be used to provide a [CoroutineScope].
 * - Changing the context of emission is prohibited, no matter whether it is `withContext(ctx)` or
 *   a builder argument (e.g. `launch(ctx)`).
 * - Collecting another flow from a separate context is allowed, but it has the same effect as
 *   applying the [flowOn] operator to that flow, which is more efficient.
 *
 * ### Exception transparency
 *
 * When `emit` or `emitAll` throws, the Flow implementations must immediately stop emitting new values and finish with an exception.
 * For diagnostics or application-specific purposes, the exception may be different from the one thrown by the emit operation,
 * suppressing the original exception as discussed below.
 * If there is a need to emit values after the downstream failed, please use the [catch][Flow.catch] operator.
 *
 * The [catch][Flow.catch] operator only catches upstream exceptions, but passes
 * all downstream exceptions. Similarly, terminal operators like [collect][Flow.collect]
 * throw any unhandled exceptions that occur in their code or in upstream flows, for example:
 *
 * ```
 * flow { emitData() }
 *     .map { computeOne(it) }
 *     .catch { ... } // catches exceptions in emitData and computeOne
 *     .map { computeTwo(it) }
 *     .collect { process(it) } // throws exceptions from process and computeTwo
 * ```
 * The same reasoning can be applied to the [onCompletion] operator that is a declarative replacement for the `finally` block.
 *
 * All exception-handling Flow operators follow the principle of exception suppression:
 *
 * If the upstream flow throws an exception during its completion when the downstream exception has been thrown,
 * the downstream exception becomes superseded and suppressed by the upstream exception, being a semantic
 * equivalent of throwing from `finally` block. However, this doesn't affect the operation of the exception-handling operators,
 * which consider the downstream exception to be the root cause and behave as if the upstream didn't throw anything.
 *
 * Failure to adhere to the exception transparency requirement can lead to strange behaviors which make
 * it hard to reason about the code because an exception in the `collect { ... }` could be somehow "caught"
 * by an upstream flow, limiting the ability of local reasoning about the code.
 *
 * Flow machinery enforces exception transparency at runtime and throws [IllegalStateException] on any attempt to emit a value,
 * if an exception has been thrown on previous attempt.
 *
 * ### Reactive streams
 *
 * Flow is [Reactive Streams](http://www.reactive-streams.org/) compliant, you can safely interop it with
 * reactive streams using `Flow.asPublisher` and `Publisher.asFlow` from `kotlinx-coroutines-reactive` module.
 *
 * ### Not stable for inheritance
 *
 * **The `Flow` interface is not stable for inheritance in 3rd party libraries**, as new methods
 * might be added to this interface in the future, but is stable for use.
 *
 * Use the `flow { ... }` builder function to create an implementation, or extend [AbstractFlow].
 * These implementations ensure that the context preservation property is not violated, and prevent most
 * of the developer mistakes related to concurrency, inconsistent flow dispatchers, and cancellation.
 */
__attribute__((swift_name("Kotlinx_coroutines_coreFlow")))
@protocol ComposeAppKotlinx_coroutines_coreFlow
@required

/**
 * Accepts the given [collector] and [emits][FlowCollector.emit] values into it.
 *
 * This method can be used along with SAM-conversion of [FlowCollector]:
 * ```
 * myFlow.collect { value -> println("Collected $value") }
 * ```
 *
 * ### Method inheritance
 *
 * To ensure the context preservation property, it is not recommended implementing this method directly.
 * Instead, [AbstractFlow] can be used as the base type to properly ensure flow's properties.
 *
 * All default flow implementations ensure context preservation and exception transparency properties on a best-effort basis
 * and throw [IllegalStateException] if a violation was detected.
 *
 * @note This method converts instances of CancellationException to errors.
 * Other uncaught Kotlin exceptions are fatal.
*/
- (void)collectCollector:(id<ComposeAppKotlinx_coroutines_coreFlowCollector>)collector completionHandler:(void (^)(NSError * _Nullable))completionHandler __attribute__((swift_name("collect(collector:completionHandler:)")));
@end


/**
 * A _hot_ [Flow] that shares emitted values among all its collectors in a broadcast fashion, so that all collectors
 * get all emitted values. A shared flow is called _hot_ because its active instance exists independently of the
 * presence of collectors. This is opposed to a regular [Flow], such as defined by the [`flow { ... }`][flow] function,
 * which is _cold_ and is started separately for each collector.
 *
 * **Shared flow never completes**. A call to [Flow.collect] on a shared flow never completes normally, and
 * neither does a coroutine started by the [Flow.launchIn] function. An active collector of a shared flow is called a _subscriber_.
 *
 * A subscriber of a shared flow can be cancelled. This usually happens when the scope in which the coroutine is running
 * is cancelled. A subscriber to a shared flow is always [cancellable][Flow.cancellable], and checks for
 * cancellation before each emission. Note that most terminal operators like [Flow.toList] would also not complete,
 * when applied to a shared flow, but flow-truncating operators like [Flow.take] and [Flow.takeWhile] can be used on a
 * shared flow to turn it into a completing one.
 *
 * A [mutable shared flow][MutableSharedFlow] is created using the [MutableSharedFlow(...)] constructor function.
 * Its state can be updated by [emitting][MutableSharedFlow.emit] values to it and performing other operations.
 * See the [MutableSharedFlow] documentation for details.
 *
 * [SharedFlow] is useful for broadcasting events that happen inside an application to subscribers that can come and go.
 * For example, the following class encapsulates an event bus that distributes events to all subscribers
 * in a _rendezvous_ manner, suspending until all subscribers receive emitted event:
 *
 * ```
 * class EventBus {
 *     private val _events = MutableSharedFlow<Event>() // private mutable shared flow
 *     val events = _events.asSharedFlow() // publicly exposed as read-only shared flow
 *
 *     suspend fun produceEvent(event: Event) {
 *         _events.emit(event) // suspends until all subscribers receive it
 *     }
 * }
 * ```
 *
 * As an alternative to the above usage with the `MutableSharedFlow(...)` constructor function,
 * any _cold_ [Flow] can be converted to a shared flow using the [shareIn] operator.
 *
 * There is a specialized implementation of shared flow for the case where the most recent state value needs
 * to be shared. See [StateFlow] for details.
 *
 * ### Replay cache and buffer
 *
 * A shared flow keeps a specific number of the most recent values in its _replay cache_. Every new subscriber first
 * gets the values from the replay cache and then gets new emitted values. The maximum size of the replay cache is
 * specified when the shared flow is created by the `replay` parameter. A snapshot of the current replay cache
 * is available via the [replayCache] property and it can be reset with the [MutableSharedFlow.resetReplayCache] function.
 *
 * A replay cache also provides buffer for emissions to the shared flow, allowing slow subscribers to
 * get values from the buffer without suspending emitters. The buffer space determines how much slow subscribers
 * can lag from the fast ones. When creating a shared flow, additional buffer capacity beyond replay can be reserved
 * using the `extraBufferCapacity` parameter.
 *
 * A shared flow with a buffer can be configured to avoid suspension of emitters on buffer overflow using
 * the `onBufferOverflow` parameter, which is equal to one of the entries of the [BufferOverflow] enum. When a strategy other
 * than [SUSPENDED][BufferOverflow.SUSPEND] is configured, emissions to the shared flow never suspend.
 *
 * **Buffer overflow condition can happen only when there is at least one subscriber that is not ready to accept
 * the new value.**  In the absence of subscribers only the most recent `replay` values are stored and the buffer
 * overflow behavior is never triggered and has no effect. In particular, in the absence of subscribers emitter never
 * suspends despite [BufferOverflow.SUSPEND] option and [BufferOverflow.DROP_LATEST] option does not have effect either.
 * Essentially, the behavior in the absence of subscribers is always similar to [BufferOverflow.DROP_OLDEST],
 * but the buffer is just of `replay` size (without any `extraBufferCapacity`).
 *
 * ### Unbuffered shared flow
 *
 * A default implementation of a shared flow that is created with `MutableSharedFlow()` constructor function
 * without parameters has no replay cache nor additional buffer.
 * [emit][MutableSharedFlow.emit] call to such a shared flow suspends until all subscribers receive the emitted value
 * and returns immediately if there are no subscribers.
 * Thus, [tryEmit][MutableSharedFlow.tryEmit] call succeeds and returns `true` only if
 * there are no subscribers (in which case the emitted value is immediately lost).
 *
 * ### SharedFlow vs BroadcastChannel
 *
 * Conceptually shared flow is similar to [BroadcastChannel][BroadcastChannel]
 * and is designed to completely replace it.
 * It has the following important differences:
 *
 * - `SharedFlow` is simpler, because it does not have to implement all the [Channel] APIs, which allows
 *   for faster and simpler implementation.
 * - `SharedFlow` supports configurable replay and buffer overflow strategy.
 * - `SharedFlow` has a clear separation into a read-only `SharedFlow` interface and a [MutableSharedFlow].
 * - `SharedFlow` cannot be closed like `BroadcastChannel` and can never represent a failure.
 *   All errors and completion signals should be explicitly _materialized_ if needed.
 *
 * To migrate [BroadcastChannel] usage to [SharedFlow], start by replacing usages of the `BroadcastChannel(capacity)`
 * constructor with `MutableSharedFlow(0, extraBufferCapacity=capacity)` (broadcast channel does not replay
 * values to new subscribers). Replace [send][BroadcastChannel.send] and [trySend][BroadcastChannel.trySend] calls
 * with [emit][MutableStateFlow.emit] and [tryEmit][MutableStateFlow.tryEmit], and convert subscribers' code to flow operators.
 *
 * ### Concurrency
 *
 * All methods of shared flow are **thread-safe** and can be safely invoked from concurrent coroutines without
 * external synchronization.
 *
 * ### Operator fusion
 *
 * Application of [flowOn][Flow.flowOn], [buffer] with [RENDEZVOUS][Channel.RENDEZVOUS] capacity,
 * or [cancellable] operators to a shared flow has no effect.
 *
 * ### Implementation notes
 *
 * Shared flow implementation uses a lock to ensure thread-safety, but suspending collector and emitter coroutines are
 * resumed outside of this lock to avoid deadlocks when using unconfined coroutines. Adding new subscribers
 * has `O(1)` amortized cost, but emitting has `O(N)` cost, where `N` is the number of subscribers.
 *
 * ### Not stable for inheritance
 *
 * **The `SharedFlow` interface is not stable for inheritance in 3rd party libraries**, as new methods
 * might be added to this interface in the future, but is stable for use.
 * Use the `MutableSharedFlow(replay, ...)` constructor function to create an implementation.
 *
 * @note annotations
 *   kotlin.SubclassOptInRequired(markerClass=[NormalClass(value=kotlinx/coroutines/ExperimentalForInheritanceCoroutinesApi)])
*/
__attribute__((swift_name("Kotlinx_coroutines_coreSharedFlow")))
@protocol ComposeAppKotlinx_coroutines_coreSharedFlow <ComposeAppKotlinx_coroutines_coreFlow>
@required

/**
 * A snapshot of the replay cache.
 */
@property (readonly) NSArray<id> *replayCache __attribute__((swift_name("replayCache")));
@end


/**
 * A [SharedFlow] that represents a read-only state with a single updatable data [value] that emits updates
 * to the value to its collectors. A state flow is a _hot_ flow because its active instance exists independently
 * of the presence of collectors. Its current value can be retrieved via the [value] property.
 *
 * **State flow never completes**. A call to [Flow.collect] on a state flow never completes normally, and
 * neither does a coroutine started by the [Flow.launchIn] function. An active collector of a state flow is called a _subscriber_.
 *
 * A [mutable state flow][MutableStateFlow] is created using `MutableStateFlow(value)` constructor function with
 * the initial value. The value of mutable state flow can be updated by setting its [value] property.
 * Updates to the [value] are always [conflated][Flow.conflate]. So a slow collector skips fast updates,
 * but always collects the most recently emitted value.
 *
 * [StateFlow] is useful as a data-model class to represent any kind of state.
 * Derived values can be defined using various operators on the flows, with [combine] operator being especially
 * useful to combine values from multiple state flows using arbitrary functions.
 *
 * For example, the following class encapsulates an integer state and increments its value on each call to `inc`:
 *
 * ```
 * class CounterModel {
 *     private val _counter = MutableStateFlow(0) // private mutable state flow
 *     val counter = _counter.asStateFlow() // publicly exposed as read-only state flow
 *
 *     fun inc() {
 *         _counter.update { count -> count + 1 } // atomic, safe for concurrent use
 *     }
 * }
 * ```
 *
 * Having two instances of the above `CounterModel` class one can define the sum of their counters like this:
 *
 * ```
 * val aModel = CounterModel()
 * val bModel = CounterModel()
 * val sumFlow: Flow<Int> = aModel.counter.combine(bModel.counter) { a, b -> a + b }
 * ```
 *
 * As an alternative to the above usage with the `MutableStateFlow(...)` constructor function,
 * any _cold_ [Flow] can be converted to a state flow using the [stateIn] operator.
 *
 * ### Strong equality-based conflation
 *
 * Values in state flow are conflated using [Any.equals] comparison in a similar way to
 * [distinctUntilChanged] operator. It is used to conflate incoming updates
 * to [value][MutableStateFlow.value] in [MutableStateFlow] and to suppress emission of the values to collectors
 * when new value is equal to the previously emitted one. State flow behavior with classes that violate
 * the contract for [Any.equals] is unspecified.
 *
 * ### State flow is a shared flow
 *
 * State flow is a special-purpose, high-performance, and efficient implementation of [SharedFlow] for the narrow,
 * but widely used case of sharing a state. See the [SharedFlow] documentation for the basic rules,
 * constraints, and operators that are applicable to all shared flows.
 *
 * State flow always has an initial value, replays one most recent value to new subscribers, does not buffer any
 * more values, but keeps the last emitted one, and does not support [resetReplayCache][MutableSharedFlow.resetReplayCache].
 * A state flow behaves identically to a shared flow when it is created
 * with the following parameters and the [distinctUntilChanged] operator is applied to it:
 *
 * ```
 * // MutableStateFlow(initialValue) is a shared flow with the following parameters:
 * val shared = MutableSharedFlow(
 *     replay = 1,
 *     onBufferOverflow = BufferOverflow.DROP_OLDEST
 * )
 * shared.tryEmit(initialValue) // emit the initial value
 * val state = shared.distinctUntilChanged() // get StateFlow-like behavior
 * ```
 *
 * Use [SharedFlow] when you need a [StateFlow] with tweaks in its behavior such as extra buffering, replaying more
 * values, or omitting the initial value.
 *
 * ### StateFlow vs ConflatedBroadcastChannel
 *
 * Conceptually, state flow is similar to [ConflatedBroadcastChannel]
 * and is designed to completely replace it.
 * It has the following important differences:
 *
 * - `StateFlow` is simpler, because it does not have to implement all the [Channel] APIs, which allows
 *   for faster, garbage-free implementation, unlike `ConflatedBroadcastChannel` implementation that
 *   allocates objects on each emitted value.
 * - `StateFlow` always has a value which can be safely read at any time via [value] property.
 *   Unlike `ConflatedBroadcastChannel`, there is no way to create a state flow without a value.
 * - `StateFlow` has a clear separation into a read-only `StateFlow` interface and a [MutableStateFlow].
 * - `StateFlow` conflation is based on equality like [distinctUntilChanged] operator,
 *   unlike conflation in `ConflatedBroadcastChannel` that is based on reference identity.
 * - `StateFlow` cannot be closed like `ConflatedBroadcastChannel` and can never represent a failure.
 *   All errors and completion signals should be explicitly _materialized_ if needed.
 *
 * `StateFlow` is designed to better cover typical use-cases of keeping track of state changes in time, taking
 * more pragmatic design choices for the sake of convenience.
 *
 * To migrate [ConflatedBroadcastChannel] usage to [StateFlow], start by replacing usages of the `ConflatedBroadcastChannel()`
 * constructor with `MutableStateFlow(initialValue)`, using `null` as an initial value if you don't have one.
 * Replace [send][ConflatedBroadcastChannel.send] and [trySend][ConflatedBroadcastChannel.trySend] calls
 * with updates to the state flow's [MutableStateFlow.value], and convert subscribers' code to flow operators.
 * You can use the [filterNotNull] operator to mimic behavior of a `ConflatedBroadcastChannel` without initial value.
 *
 * ### Concurrency
 *
 * All methods of state flow are **thread-safe** and can be safely invoked from concurrent coroutines without
 * external synchronization.
 *
 * ### Operator fusion
 *
 * Application of [flowOn][Flow.flowOn], [conflate][Flow.conflate],
 * [buffer] with [CONFLATED][Channel.CONFLATED] or [RENDEZVOUS][Channel.RENDEZVOUS] capacity,
 * [distinctUntilChanged][Flow.distinctUntilChanged], or [cancellable] operators to a state flow has no effect.
 *
 * ### Implementation notes
 *
 * State flow implementation is optimized for memory consumption and allocation-freedom. It uses a lock to ensure
 * thread-safety, but suspending collector coroutines are resumed outside of this lock to avoid dead-locks when
 * using unconfined coroutines. Adding new subscribers has `O(1)` amortized cost, but updating a [value] has `O(N)`
 * cost, where `N` is the number of active subscribers.
 *
 * ### Not stable for inheritance
 *
 * **`The StateFlow` interface is not stable for inheritance in 3rd party libraries**, as new methods
 * might be added to this interface in the future, but is stable for use.
 * Use the `MutableStateFlow(value)` constructor function to create an implementation.
 *
 * @note annotations
 *   kotlin.SubclassOptInRequired(markerClass=[NormalClass(value=kotlinx/coroutines/ExperimentalForInheritanceCoroutinesApi)])
*/
__attribute__((swift_name("Kotlinx_coroutines_coreStateFlow")))
@protocol ComposeAppKotlinx_coroutines_coreStateFlow <ComposeAppKotlinx_coroutines_coreSharedFlow>
@required

/**
 * The current value of this state flow.
 */
@property (readonly) id _Nullable value __attribute__((swift_name("value")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinByteArray")))
@interface ComposeAppKotlinByteArray : ComposeAppBase
+ (instancetype)arrayWithSize:(int32_t)size __attribute__((swift_name("init(size:)")));
+ (instancetype)arrayWithSize:(int32_t)size init:(ComposeAppByte *(^)(ComposeAppInt *))init __attribute__((swift_name("init(size:init:)")));
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (int8_t)getIndex:(int32_t)index __attribute__((swift_name("get(index:)")));
- (ComposeAppKotlinByteIterator *)iterator __attribute__((swift_name("iterator()")));
- (void)setIndex:(int32_t)index value:(int8_t)value __attribute__((swift_name("set(index:value:)")));
@property (readonly) int32_t size __attribute__((swift_name("size")));
@end


/**
 * Represents a resource with an ID and a set of resource items.
 *
 * @property id The ID of the resource.
 * @property items The set of resource items associated with the resource.
 *
 * @note annotations
 *   androidx.compose.runtime.Immutable
*/
__attribute__((swift_name("LibraryResource")))
@interface ComposeAppLibraryResource : ComposeAppBase
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
@end


/**
 * Represents a drawable resource.
 *
 * @param id The unique identifier of the drawable resource.
 * @param items The set of resource items associated with the image resource.
 *
 * @note annotations
 *   androidx.compose.runtime.Immutable
*/
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LibraryDrawableResource")))
@interface ComposeAppLibraryDrawableResource : ComposeAppLibraryResource
- (instancetype)initWithId:(NSString *)id items:(NSSet<ComposeAppLibraryResourceItem *> *)items __attribute__((swift_name("init(id:items:)"))) __attribute__((objc_designated_initializer));
@end


/**
 * Represents a font resource.
 *
 * @param id The identifier of the font resource.
 * @param items The set of resource items associated with the font resource.
 *
 * @see Resource
 *
 * @note annotations
 *   androidx.compose.runtime.Immutable
*/
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LibraryFontResource")))
@interface ComposeAppLibraryFontResource : ComposeAppLibraryResource
- (instancetype)initWithId:(NSString *)id items:(NSSet<ComposeAppLibraryResourceItem *> *)items __attribute__((swift_name("init(id:items:)"))) __attribute__((objc_designated_initializer));
@end


/**
 * Represents a quantity string resource in the application.
 *
 * @param id The unique identifier of the resource.
 * @param key The key used to retrieve the string resource.
 * @param items The set of resource items associated with the string resource.
 *
 * @note annotations
 *   androidx.compose.runtime.Immutable
*/
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LibraryPluralStringResource")))
@interface ComposeAppLibraryPluralStringResource : ComposeAppLibraryResource
- (instancetype)initWithId:(NSString *)id key:(NSString *)key items:(NSSet<ComposeAppLibraryResourceItem *> *)items __attribute__((swift_name("init(id:key:items:)"))) __attribute__((objc_designated_initializer));
@property (readonly) NSString *key __attribute__((swift_name("key")));
@end


/**
 * Represents a string array resource in the application.
 *
 * @param id The unique identifier of the resource.
 * @param key The key used to retrieve the string array resource.
 * @param items The set of resource items associated with the string array resource.
 *
 * @note annotations
 *   androidx.compose.runtime.Immutable
*/
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LibraryStringArrayResource")))
@interface ComposeAppLibraryStringArrayResource : ComposeAppLibraryResource
- (instancetype)initWithId:(NSString *)id key:(NSString *)key items:(NSSet<ComposeAppLibraryResourceItem *> *)items __attribute__((swift_name("init(id:key:items:)"))) __attribute__((objc_designated_initializer));
@property (readonly) NSString *key __attribute__((swift_name("key")));
@end


/**
 * Represents a string resource in the application.
 *
 * @param id The unique identifier of the resource.
 * @param key The key used to retrieve the string resource.
 * @param items The set of resource items associated with the string resource.
 *
 * @note annotations
 *   androidx.compose.runtime.Immutable
*/
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LibraryStringResource")))
@interface ComposeAppLibraryStringResource : ComposeAppLibraryResource
- (instancetype)initWithId:(NSString *)id key:(NSString *)key items:(NSSet<ComposeAppLibraryResourceItem *> *)items __attribute__((swift_name("init(id:key:items:)"))) __attribute__((objc_designated_initializer));
@property (readonly) NSString *key __attribute__((swift_name("key")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreModule")))
@interface ComposeAppKoin_coreModule : ComposeAppBase
- (instancetype)initWith_createdAtStart:(BOOL)_createdAtStart __attribute__((swift_name("init(_createdAtStart:)"))) __attribute__((objc_designated_initializer));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (ComposeAppKoin_coreKoinDefinition<id> *)factoryQualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier definition:(id _Nullable (^)(ComposeAppKoin_coreScope *, ComposeAppKoin_coreParametersHolder *))definition __attribute__((swift_name("factory(qualifier:definition:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (void)includesModule:(ComposeAppKotlinArray<ComposeAppKoin_coreModule *> *)module __attribute__((swift_name("includes(module:)")));
- (void)includesModule_:(id)module __attribute__((swift_name("includes(module_:)")));
- (void)indexPrimaryTypeInstanceFactory:(ComposeAppKoin_coreInstanceFactory<id> *)instanceFactory __attribute__((swift_name("indexPrimaryType(instanceFactory:)")));
- (void)indexSecondaryTypesInstanceFactory:(ComposeAppKoin_coreInstanceFactory<id> *)instanceFactory __attribute__((swift_name("indexSecondaryTypes(instanceFactory:)")));
- (NSArray<ComposeAppKoin_coreModule *> *)plusModules:(NSArray<ComposeAppKoin_coreModule *> *)modules __attribute__((swift_name("plus(modules:)")));
- (NSArray<ComposeAppKoin_coreModule *> *)plusModule:(ComposeAppKoin_coreModule *)module __attribute__((swift_name("plus(module:)")));
- (void)prepareForCreationAtStartInstanceFactory:(ComposeAppKoin_coreSingleInstanceFactory<id> *)instanceFactory __attribute__((swift_name("prepareForCreationAtStart(instanceFactory:)")));
- (void)scopeScopeSet:(void (^)(ComposeAppKoin_coreScopeDSL *))scopeSet __attribute__((swift_name("scope(scopeSet:)")));
- (void)scopeQualifier:(id<ComposeAppKoin_coreQualifier>)qualifier scopeSet:(void (^)(ComposeAppKoin_coreScopeDSL *))scopeSet __attribute__((swift_name("scope(qualifier:scopeSet:)")));
- (ComposeAppKoin_coreKoinDefinition<id> *)singleQualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier createdAtStart:(BOOL)createdAtStart definition:(id _Nullable (^)(ComposeAppKoin_coreScope *, ComposeAppKoin_coreParametersHolder *))definition __attribute__((swift_name("single(qualifier:createdAtStart:definition:)")));
@property (readonly) ComposeAppMutableSet<ComposeAppKoin_coreSingleInstanceFactory<id> *> *eagerInstances __attribute__((swift_name("eagerInstances")));
@property (readonly) NSString *id __attribute__((swift_name("id")));
@property (readonly) NSMutableArray<ComposeAppKoin_coreModule *> *includedModules __attribute__((swift_name("includedModules")));
@property (readonly) BOOL isLoaded __attribute__((swift_name("isLoaded")));
@property (readonly) ComposeAppMutableDictionary<NSString *, ComposeAppKoin_coreInstanceFactory<id> *> *mappings __attribute__((swift_name("mappings")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinArray")))
@interface ComposeAppKotlinArray<T> : ComposeAppBase
+ (instancetype)arrayWithSize:(int32_t)size init:(T _Nullable (^)(ComposeAppInt *))init __attribute__((swift_name("init(size:init:)")));
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (T _Nullable)getIndex:(int32_t)index __attribute__((swift_name("get(index:)")));
- (id<ComposeAppKotlinIterator>)iterator __attribute__((swift_name("iterator()")));
- (void)setIndex:(int32_t)index value:(T _Nullable)value __attribute__((swift_name("set(index:value:)")));
@property (readonly) int32_t size __attribute__((swift_name("size")));
@end


/**
 * [FlowCollector] is used as an intermediate or a terminal collector of the flow and represents
 * an entity that accepts values emitted by the [Flow].
 *
 * This interface should usually not be implemented directly, but rather used as a receiver in a [flow] builder when implementing a custom operator,
 * or with SAM-conversion.
 * Implementations of this interface are not thread-safe.
 *
 * Example of usage:
 *
 * ```
 * val flow = getMyEvents()
 * try {
 *     flow.collect { value ->
 *         println("Received $value")
 *     }
 *     println("My events are consumed successfully")
 * } catch (e: Throwable) {
 *     println("Exception from the flow: $e")
 * }
 * ```
 */
__attribute__((swift_name("Kotlinx_coroutines_coreFlowCollector")))
@protocol ComposeAppKotlinx_coroutines_coreFlowCollector
@required

/**
 * Collects the value emitted by the upstream.
 * This method is not thread-safe and should not be invoked concurrently.
 *
 * @note This method converts instances of CancellationException to errors.
 * Other uncaught Kotlin exceptions are fatal.
*/
- (void)emitValue:(id _Nullable)value completionHandler:(void (^)(NSError * _Nullable))completionHandler __attribute__((swift_name("emit(value:completionHandler:)")));
@end

__attribute__((swift_name("KotlinIterator")))
@protocol ComposeAppKotlinIterator
@required
- (BOOL)hasNext __attribute__((swift_name("hasNext()")));
- (id _Nullable)next __attribute__((swift_name("next()")));
@end

__attribute__((swift_name("KotlinByteIterator")))
@interface ComposeAppKotlinByteIterator : ComposeAppBase <ComposeAppKotlinIterator>
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (ComposeAppByte *)next __attribute__((swift_name("next()")));
- (int8_t)nextByte __attribute__((swift_name("nextByte()")));
@end


/**
 * Represents a resource item with qualifiers and a path.
 *
 * @property qualifiers The qualifiers of the resource item.
 * @property path The path of the resource item.
 * @property offset The offset in bytes of the resource in the file. '-1' means the resource is whole file
 * @property size The size in bytes of the resource in the file. '-1' means the resource is whole file
 *
 * @note annotations
 *   androidx.compose.runtime.Immutable
*/
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("LibraryResourceItem")))
@interface ComposeAppLibraryResourceItem : ComposeAppBase
- (instancetype)initWithQualifiers:(NSSet<id<ComposeAppLibraryQualifier>> *)qualifiers path:(NSString *)path offset:(int64_t)offset size:(int64_t)size __attribute__((swift_name("init(qualifiers:path:offset:size:)"))) __attribute__((objc_designated_initializer));
- (ComposeAppLibraryResourceItem *)doCopyQualifiers:(NSSet<id<ComposeAppLibraryQualifier>> *)qualifiers path:(NSString *)path offset:(int64_t)offset size:(int64_t)size __attribute__((swift_name("doCopy(qualifiers:path:offset:size:)")));

/**
 * Represents a resource item with qualifiers and a path.
 *
 * @property qualifiers The qualifiers of the resource item.
 * @property path The path of the resource item.
 * @property offset The offset in bytes of the resource in the file. '-1' means the resource is whole file
 * @property size The size in bytes of the resource in the file. '-1' means the resource is whole file
 */
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));

/**
 * Represents a resource item with qualifiers and a path.
 *
 * @property qualifiers The qualifiers of the resource item.
 * @property path The path of the resource item.
 * @property offset The offset in bytes of the resource in the file. '-1' means the resource is whole file
 * @property size The size in bytes of the resource in the file. '-1' means the resource is whole file
 */
- (NSUInteger)hash __attribute__((swift_name("hash()")));

/**
 * Represents a resource item with qualifiers and a path.
 *
 * @property qualifiers The qualifiers of the resource item.
 * @property path The path of the resource item.
 * @property offset The offset in bytes of the resource in the file. '-1' means the resource is whole file
 * @property size The size in bytes of the resource in the file. '-1' means the resource is whole file
 */
- (NSString *)description __attribute__((swift_name("description()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreKoinDefinition")))
@interface ComposeAppKoin_coreKoinDefinition<R> : ComposeAppBase
- (instancetype)initWithModule:(ComposeAppKoin_coreModule *)module factory:(ComposeAppKoin_coreInstanceFactory<R> *)factory __attribute__((swift_name("init(module:factory:)"))) __attribute__((objc_designated_initializer));
- (ComposeAppKoin_coreKoinDefinition<R> *)doCopyModule:(ComposeAppKoin_coreModule *)module factory:(ComposeAppKoin_coreInstanceFactory<R> *)factory __attribute__((swift_name("doCopy(module:factory:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) ComposeAppKoin_coreInstanceFactory<R> *factory __attribute__((swift_name("factory")));
@property (readonly) ComposeAppKoin_coreModule *module __attribute__((swift_name("module")));
@end

__attribute__((swift_name("Koin_coreQualifier")))
@protocol ComposeAppKoin_coreQualifier
@required
@property (readonly) NSString *value __attribute__((swift_name("value")));
@end

__attribute__((swift_name("Koin_coreLockable")))
@interface ComposeAppKoin_coreLockable : ComposeAppBase
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreScope")))
@interface ComposeAppKoin_coreScope : ComposeAppKoin_coreLockable
- (instancetype)initWithScopeQualifier:(id<ComposeAppKoin_coreQualifier>)scopeQualifier id:(NSString *)id isRoot:(BOOL)isRoot _koin:(ComposeAppKoin_coreKoin *)_koin __attribute__((swift_name("init(scopeQualifier:id:isRoot:_koin:)"))) __attribute__((objc_designated_initializer));
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
+ (instancetype)new __attribute__((unavailable));
- (void)close __attribute__((swift_name("close()")));
- (void)declareInstance:(id _Nullable)instance qualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier secondaryTypes:(NSArray<id<ComposeAppKotlinKClass>> *)secondaryTypes allowOverride:(BOOL)allowOverride __attribute__((swift_name("declare(instance:qualifier:secondaryTypes:allowOverride:)")));
- (id)getQualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier parameters:(ComposeAppKoin_coreParametersHolder *(^ _Nullable)(void))parameters __attribute__((swift_name("get(qualifier:parameters:)")));
- (id _Nullable)getClazz:(id<ComposeAppKotlinKClass>)clazz qualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier parameters:(ComposeAppKoin_coreParametersHolder *(^ _Nullable)(void))parameters __attribute__((swift_name("get(clazz:qualifier:parameters:)")));
- (NSArray<id> *)getAll __attribute__((swift_name("getAll()")));
- (NSArray<id> *)getAllClazz:(id<ComposeAppKotlinKClass>)clazz __attribute__((swift_name("getAll(clazz:)")));
- (ComposeAppKoin_coreKoin *)getKoin __attribute__((swift_name("getKoin()")));
- (id _Nullable)getOrNullQualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier parameters:(ComposeAppKoin_coreParametersHolder *(^ _Nullable)(void))parameters __attribute__((swift_name("getOrNull(qualifier:parameters:)")));
- (id _Nullable)getOrNullClazz:(id<ComposeAppKotlinKClass>)clazz qualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier parameters:(ComposeAppKoin_coreParametersHolder *(^ _Nullable)(void))parameters __attribute__((swift_name("getOrNull(clazz:qualifier:parameters:)")));
- (id)getPropertyKey:(NSString *)key __attribute__((swift_name("getProperty(key:)")));
- (id)getPropertyKey:(NSString *)key defaultValue:(id)defaultValue __attribute__((swift_name("getProperty(key:defaultValue:)")));
- (id _Nullable)getPropertyOrNullKey:(NSString *)key __attribute__((swift_name("getPropertyOrNull(key:)")));
- (ComposeAppKoin_coreScope *)getScopeScopeID:(NSString *)scopeID __attribute__((swift_name("getScope(scopeID:)")));
- (id _Nullable)getSource __attribute__((swift_name("getSource()")));
- (id<ComposeAppKotlinLazy>)injectQualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier mode:(ComposeAppKotlinLazyThreadSafetyMode *)mode parameters:(ComposeAppKoin_coreParametersHolder *(^ _Nullable)(void))parameters __attribute__((swift_name("inject(qualifier:mode:parameters:)")));
- (id<ComposeAppKotlinLazy>)injectOrNullQualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier mode:(ComposeAppKotlinLazyThreadSafetyMode *)mode parameters:(ComposeAppKoin_coreParametersHolder *(^ _Nullable)(void))parameters __attribute__((swift_name("injectOrNull(qualifier:mode:parameters:)")));
- (BOOL)isNotClosed __attribute__((swift_name("isNotClosed()")));
- (void)linkToScopes:(ComposeAppKotlinArray<ComposeAppKoin_coreScope *> *)scopes __attribute__((swift_name("linkTo(scopes:)")));
- (void)registerCallbackCallback:(id<ComposeAppKoin_coreScopeCallback>)callback __attribute__((swift_name("registerCallback(callback:)")));
- (NSString *)description __attribute__((swift_name("description()")));
- (void)unlinkScopes:(ComposeAppKotlinArray<ComposeAppKoin_coreScope *> *)scopes __attribute__((swift_name("unlink(scopes:)")));
@property (readonly) BOOL closed __attribute__((swift_name("closed")));
@property (readonly) NSString *id __attribute__((swift_name("id")));
@property (readonly) BOOL isRoot __attribute__((swift_name("isRoot")));
@property (readonly) ComposeAppKoin_coreLogger *logger __attribute__((swift_name("logger")));
@property (readonly) id<ComposeAppKoin_coreQualifier> scopeQualifier __attribute__((swift_name("scopeQualifier")));
@property id _Nullable sourceValue __attribute__((swift_name("sourceValue")));
@end

__attribute__((swift_name("Koin_coreParametersHolder")))
@interface ComposeAppKoin_coreParametersHolder : ComposeAppBase
- (instancetype)initWith_values:(NSMutableArray<id> *)_values useIndexedValues:(ComposeAppBoolean * _Nullable)useIndexedValues __attribute__((swift_name("init(_values:useIndexedValues:)"))) __attribute__((objc_designated_initializer));
- (ComposeAppKoin_coreParametersHolder *)addValue:(id)value __attribute__((swift_name("add(value:)")));
- (id _Nullable)component1 __attribute__((swift_name("component1()")));
- (id _Nullable)component2 __attribute__((swift_name("component2()")));
- (id _Nullable)component3 __attribute__((swift_name("component3()")));
- (id _Nullable)component4 __attribute__((swift_name("component4()")));
- (id _Nullable)component5 __attribute__((swift_name("component5()")));
- (id _Nullable)elementAtI:(int32_t)i clazz:(id<ComposeAppKotlinKClass>)clazz __attribute__((swift_name("elementAt(i:clazz:)")));
- (id)get __attribute__((swift_name("get()")));
- (id _Nullable)getI:(int32_t)i __attribute__((swift_name("get(i:)")));
- (id _Nullable)getOrNull __attribute__((swift_name("getOrNull()")));
- (id _Nullable)getOrNullClazz:(id<ComposeAppKotlinKClass>)clazz __attribute__((swift_name("getOrNull(clazz:)")));
- (ComposeAppKoin_coreParametersHolder *)insertIndex:(int32_t)index value:(id)value __attribute__((swift_name("insert(index:value:)")));
- (BOOL)isEmpty __attribute__((swift_name("isEmpty()")));
- (BOOL)isNotEmpty __attribute__((swift_name("isNotEmpty()")));
- (void)setI:(int32_t)i t:(id _Nullable)t __attribute__((swift_name("set(i:t:)")));
- (int32_t)size __attribute__((swift_name("size()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property int32_t index __attribute__((swift_name("index")));
@property (readonly) ComposeAppBoolean * _Nullable useIndexedValues __attribute__((swift_name("useIndexedValues")));
@property (readonly) NSArray<id> *values __attribute__((swift_name("values")));
@end

__attribute__((swift_name("Koin_coreInstanceFactory")))
@interface ComposeAppKoin_coreInstanceFactory<T> : ComposeAppKoin_coreLockable
- (instancetype)initWithBeanDefinition:(ComposeAppKoin_coreBeanDefinition<T> *)beanDefinition __attribute__((swift_name("init(beanDefinition:)"))) __attribute__((objc_designated_initializer));
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
+ (instancetype)new __attribute__((unavailable));
@property (class, readonly, getter=companion) ComposeAppKoin_coreInstanceFactoryCompanion *companion __attribute__((swift_name("companion")));
- (T _Nullable)createContext:(ComposeAppKoin_coreResolutionContext *)context __attribute__((swift_name("create(context:)")));
- (void)dropScope:(ComposeAppKoin_coreScope * _Nullable)scope __attribute__((swift_name("drop(scope:)")));
- (void)dropAll __attribute__((swift_name("dropAll()")));
- (T _Nullable)getContext:(ComposeAppKoin_coreResolutionContext *)context __attribute__((swift_name("get(context:)")));
- (BOOL)isCreatedContext:(ComposeAppKoin_coreResolutionContext * _Nullable)context __attribute__((swift_name("isCreated(context:)")));
@property (readonly) ComposeAppKoin_coreBeanDefinition<T> *beanDefinition __attribute__((swift_name("beanDefinition")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreSingleInstanceFactory")))
@interface ComposeAppKoin_coreSingleInstanceFactory<T> : ComposeAppKoin_coreInstanceFactory<T>
- (instancetype)initWithBeanDefinition:(ComposeAppKoin_coreBeanDefinition<T> *)beanDefinition __attribute__((swift_name("init(beanDefinition:)"))) __attribute__((objc_designated_initializer));
- (T _Nullable)createContext:(ComposeAppKoin_coreResolutionContext *)context __attribute__((swift_name("create(context:)")));
- (void)dropScope:(ComposeAppKoin_coreScope * _Nullable)scope __attribute__((swift_name("drop(scope:)")));
- (void)dropAll __attribute__((swift_name("dropAll()")));
- (T _Nullable)getContext:(ComposeAppKoin_coreResolutionContext *)context __attribute__((swift_name("get(context:)")));
- (BOOL)isCreatedContext:(ComposeAppKoin_coreResolutionContext * _Nullable)context __attribute__((swift_name("isCreated(context:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreScopeDSL")))
@interface ComposeAppKoin_coreScopeDSL : ComposeAppBase
- (instancetype)initWithScopeQualifier:(id<ComposeAppKoin_coreQualifier>)scopeQualifier module:(ComposeAppKoin_coreModule *)module __attribute__((swift_name("init(scopeQualifier:module:)"))) __attribute__((objc_designated_initializer));
- (ComposeAppKoin_coreKoinDefinition<id> *)factoryQualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier definition:(id _Nullable (^)(ComposeAppKoin_coreScope *, ComposeAppKoin_coreParametersHolder *))definition __attribute__((swift_name("factory(qualifier:definition:)")));
- (ComposeAppKoin_coreKoinDefinition<id> *)scopedQualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier definition:(id _Nullable (^)(ComposeAppKoin_coreScope *, ComposeAppKoin_coreParametersHolder *))definition __attribute__((swift_name("scoped(qualifier:definition:)")));
@property (readonly) ComposeAppKoin_coreModule *module __attribute__((swift_name("module")));
@property (readonly) id<ComposeAppKoin_coreQualifier> scopeQualifier __attribute__((swift_name("scopeQualifier")));
@end

__attribute__((swift_name("LibraryQualifier")))
@protocol ComposeAppLibraryQualifier
@required
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreKoin")))
@interface ComposeAppKoin_coreKoin : ComposeAppBase
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (void)close __attribute__((swift_name("close()")));
- (void)createEagerInstances __attribute__((swift_name("createEagerInstances()")));
- (ComposeAppKoin_coreScope *)createScopeT:(id<ComposeAppKoin_coreKoinScopeComponent>)t __attribute__((swift_name("createScope(t:)")));
- (ComposeAppKoin_coreScope *)createScopeScopeId:(NSString *)scopeId __attribute__((swift_name("createScope(scopeId:)")));
- (ComposeAppKoin_coreScope *)createScopeScopeId:(NSString *)scopeId source:(id _Nullable)source __attribute__((swift_name("createScope(scopeId:source:)")));
- (ComposeAppKoin_coreScope *)createScopeScopeId:(NSString *)scopeId qualifier:(id<ComposeAppKoin_coreQualifier>)qualifier source:(id _Nullable)source __attribute__((swift_name("createScope(scopeId:qualifier:source:)")));
- (void)declareInstance:(id _Nullable)instance qualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier secondaryTypes:(NSArray<id<ComposeAppKotlinKClass>> *)secondaryTypes allowOverride:(BOOL)allowOverride __attribute__((swift_name("declare(instance:qualifier:secondaryTypes:allowOverride:)")));
- (void)deletePropertyKey:(NSString *)key __attribute__((swift_name("deleteProperty(key:)")));
- (void)deleteScopeScopeId:(NSString *)scopeId __attribute__((swift_name("deleteScope(scopeId:)")));
- (id)getQualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier parameters:(ComposeAppKoin_coreParametersHolder *(^ _Nullable)(void))parameters __attribute__((swift_name("get(qualifier:parameters:)")));
- (id _Nullable)getClazz:(id<ComposeAppKotlinKClass>)clazz qualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier parameters:(ComposeAppKoin_coreParametersHolder *(^ _Nullable)(void))parameters __attribute__((swift_name("get(clazz:qualifier:parameters:)")));
- (NSArray<id> *)getAll __attribute__((swift_name("getAll()")));
- (ComposeAppKoin_coreScope *)getOrCreateScopeScopeId:(NSString *)scopeId __attribute__((swift_name("getOrCreateScope(scopeId:)")));
- (ComposeAppKoin_coreScope *)getOrCreateScopeScopeId:(NSString *)scopeId qualifier:(id<ComposeAppKoin_coreQualifier>)qualifier source:(id _Nullable)source __attribute__((swift_name("getOrCreateScope(scopeId:qualifier:source:)")));
- (id _Nullable)getOrNullQualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier parameters:(ComposeAppKoin_coreParametersHolder *(^ _Nullable)(void))parameters __attribute__((swift_name("getOrNull(qualifier:parameters:)")));
- (id _Nullable)getOrNullClazz:(id<ComposeAppKotlinKClass>)clazz qualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier parameters:(ComposeAppKoin_coreParametersHolder *(^ _Nullable)(void))parameters __attribute__((swift_name("getOrNull(clazz:qualifier:parameters:)")));
- (id _Nullable)getPropertyKey:(NSString *)key __attribute__((swift_name("getProperty(key:)")));
- (id)getPropertyKey:(NSString *)key defaultValue:(id)defaultValue __attribute__((swift_name("getProperty(key:defaultValue:)")));
- (ComposeAppKoin_coreScope *)getScopeScopeId:(NSString *)scopeId __attribute__((swift_name("getScope(scopeId:)")));
- (ComposeAppKoin_coreScope * _Nullable)getScopeOrNullScopeId:(NSString *)scopeId __attribute__((swift_name("getScopeOrNull(scopeId:)")));
- (id<ComposeAppKotlinLazy>)injectQualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier mode:(ComposeAppKotlinLazyThreadSafetyMode *)mode parameters:(ComposeAppKoin_coreParametersHolder *(^ _Nullable)(void))parameters __attribute__((swift_name("inject(qualifier:mode:parameters:)")));
- (id<ComposeAppKotlinLazy>)injectOrNullQualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier mode:(ComposeAppKotlinLazyThreadSafetyMode *)mode parameters:(ComposeAppKoin_coreParametersHolder *(^ _Nullable)(void))parameters __attribute__((swift_name("injectOrNull(qualifier:mode:parameters:)")));
- (void)loadModulesModules:(NSArray<ComposeAppKoin_coreModule *> *)modules allowOverride:(BOOL)allowOverride createEagerInstances:(BOOL)createEagerInstances __attribute__((swift_name("loadModules(modules:allowOverride:createEagerInstances:)")));
- (void)setPropertyKey:(NSString *)key value:(id)value __attribute__((swift_name("setProperty(key:value:)")));
- (void)setupLoggerLogger:(ComposeAppKoin_coreLogger *)logger __attribute__((swift_name("setupLogger(logger:)")));
- (void)unloadModulesModules:(NSArray<ComposeAppKoin_coreModule *> *)modules __attribute__((swift_name("unloadModules(modules:)")));
@property (readonly) ComposeAppKoin_coreExtensionManager *extensionManager __attribute__((swift_name("extensionManager")));
@property (readonly) ComposeAppKoin_coreInstanceRegistry *instanceRegistry __attribute__((swift_name("instanceRegistry")));
@property (readonly) ComposeAppKoin_coreLogger *logger __attribute__((swift_name("logger")));
@property (readonly) ComposeAppKoin_corePropertyRegistry *propertyRegistry __attribute__((swift_name("propertyRegistry")));
@property (readonly) ComposeAppKoin_coreScopeRegistry *scopeRegistry __attribute__((swift_name("scopeRegistry")));
@end

__attribute__((swift_name("KotlinKDeclarationContainer")))
@protocol ComposeAppKotlinKDeclarationContainer
@required
@end

__attribute__((swift_name("KotlinKAnnotatedElement")))
@protocol ComposeAppKotlinKAnnotatedElement
@required
@end


/**
 * @note annotations
 *   kotlin.SinceKotlin(version="1.1")
*/
__attribute__((swift_name("KotlinKClassifier")))
@protocol ComposeAppKotlinKClassifier
@required
@end

__attribute__((swift_name("KotlinKClass")))
@protocol ComposeAppKotlinKClass <ComposeAppKotlinKDeclarationContainer, ComposeAppKotlinKAnnotatedElement, ComposeAppKotlinKClassifier>
@required

/**
 * @note annotations
 *   kotlin.SinceKotlin(version="1.1")
*/
- (BOOL)isInstanceValue:(id _Nullable)value __attribute__((swift_name("isInstance(value:)")));
@property (readonly) NSString * _Nullable qualifiedName __attribute__((swift_name("qualifiedName")));
@property (readonly) NSString * _Nullable simpleName __attribute__((swift_name("simpleName")));
@end

__attribute__((swift_name("KotlinLazy")))
@protocol ComposeAppKotlinLazy
@required
- (BOOL)isInitialized __attribute__((swift_name("isInitialized()")));
@property (readonly) id _Nullable value __attribute__((swift_name("value")));
@end

__attribute__((swift_name("KotlinComparable")))
@protocol ComposeAppKotlinComparable
@required
- (int32_t)compareToOther:(id _Nullable)other __attribute__((swift_name("compareTo(other:)")));
@end

__attribute__((swift_name("KotlinEnum")))
@interface ComposeAppKotlinEnum<E> : ComposeAppBase <ComposeAppKotlinComparable>
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) ComposeAppKotlinEnumCompanion *companion __attribute__((swift_name("companion")));
- (int32_t)compareToOther:(E)other __attribute__((swift_name("compareTo(other:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString *name __attribute__((swift_name("name")));
@property (readonly) int32_t ordinal __attribute__((swift_name("ordinal")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinLazyThreadSafetyMode")))
@interface ComposeAppKotlinLazyThreadSafetyMode : ComposeAppKotlinEnum<ComposeAppKotlinLazyThreadSafetyMode *>
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
@property (class, readonly) ComposeAppKotlinLazyThreadSafetyMode *synchronized __attribute__((swift_name("synchronized")));
@property (class, readonly) ComposeAppKotlinLazyThreadSafetyMode *publication __attribute__((swift_name("publication")));
@property (class, readonly) ComposeAppKotlinLazyThreadSafetyMode *none __attribute__((swift_name("none")));
+ (ComposeAppKotlinArray<ComposeAppKotlinLazyThreadSafetyMode *> *)values __attribute__((swift_name("values()")));
@property (class, readonly) NSArray<ComposeAppKotlinLazyThreadSafetyMode *> *entries __attribute__((swift_name("entries")));
@end

__attribute__((swift_name("Koin_coreScopeCallback")))
@protocol ComposeAppKoin_coreScopeCallback
@required
- (void)onScopeCloseScope:(ComposeAppKoin_coreScope *)scope __attribute__((swift_name("onScopeClose(scope:)")));
@end

__attribute__((swift_name("Koin_coreLogger")))
@interface ComposeAppKoin_coreLogger : ComposeAppBase
- (instancetype)initWithLevel:(ComposeAppKoin_coreLevel *)level __attribute__((swift_name("init(level:)"))) __attribute__((objc_designated_initializer));
- (void)debugMsg:(NSString *)msg __attribute__((swift_name("debug(msg:)")));
- (void)displayLevel:(ComposeAppKoin_coreLevel *)level msg:(NSString *)msg __attribute__((swift_name("display(level:msg:)")));
- (void)errorMsg:(NSString *)msg __attribute__((swift_name("error(msg:)")));
- (void)infoMsg:(NSString *)msg __attribute__((swift_name("info(msg:)")));
- (BOOL)isAtLvl:(ComposeAppKoin_coreLevel *)lvl __attribute__((swift_name("isAt(lvl:)")));
- (void)logLvl:(ComposeAppKoin_coreLevel *)lvl msg:(NSString *(^)(void))msg __attribute__((swift_name("log(lvl:msg:)")));
- (void)logLvl:(ComposeAppKoin_coreLevel *)lvl msg_:(NSString *)msg __attribute__((swift_name("log(lvl:msg_:)")));
- (void)warnMsg:(NSString *)msg __attribute__((swift_name("warn(msg:)")));
@property ComposeAppKoin_coreLevel *level __attribute__((swift_name("level")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreBeanDefinition")))
@interface ComposeAppKoin_coreBeanDefinition<T> : ComposeAppBase
- (instancetype)initWithScopeQualifier:(id<ComposeAppKoin_coreQualifier>)scopeQualifier primaryType:(id<ComposeAppKotlinKClass>)primaryType qualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier definition:(T _Nullable (^)(ComposeAppKoin_coreScope *, ComposeAppKoin_coreParametersHolder *))definition kind:(ComposeAppKoin_coreKind *)kind secondaryTypes:(NSArray<id<ComposeAppKotlinKClass>> *)secondaryTypes __attribute__((swift_name("init(scopeQualifier:primaryType:qualifier:definition:kind:secondaryTypes:)"))) __attribute__((objc_designated_initializer));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (BOOL)hasTypeClazz:(id<ComposeAppKotlinKClass>)clazz __attribute__((swift_name("hasType(clazz:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (BOOL)isClazz:(id<ComposeAppKotlinKClass>)clazz qualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier scopeDefinition:(id<ComposeAppKoin_coreQualifier>)scopeDefinition __attribute__((swift_name("is(clazz:qualifier:scopeDefinition:)")));
- (NSString *)description __attribute__((swift_name("description()")));
@property ComposeAppKoin_coreCallbacks<T> *callbacks __attribute__((swift_name("callbacks")));
@property (readonly) T _Nullable (^definition)(ComposeAppKoin_coreScope *, ComposeAppKoin_coreParametersHolder *) __attribute__((swift_name("definition")));
@property (readonly) ComposeAppKoin_coreKind *kind __attribute__((swift_name("kind")));
@property (readonly) id<ComposeAppKotlinKClass> primaryType __attribute__((swift_name("primaryType")));
@property id<ComposeAppKoin_coreQualifier> _Nullable qualifier __attribute__((swift_name("qualifier")));
@property (readonly) id<ComposeAppKoin_coreQualifier> scopeQualifier __attribute__((swift_name("scopeQualifier")));
@property NSArray<id<ComposeAppKotlinKClass>> *secondaryTypes __attribute__((swift_name("secondaryTypes")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreInstanceFactoryCompanion")))
@interface ComposeAppKoin_coreInstanceFactoryCompanion : ComposeAppBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppKoin_coreInstanceFactoryCompanion *shared __attribute__((swift_name("shared")));
@property (readonly) NSString *ERROR_SEPARATOR __attribute__((swift_name("ERROR_SEPARATOR")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreResolutionContext")))
@interface ComposeAppKoin_coreResolutionContext : ComposeAppBase
- (instancetype)initWithLogger:(ComposeAppKoin_coreLogger *)logger scope:(ComposeAppKoin_coreScope *)scope clazz:(id<ComposeAppKotlinKClass>)clazz qualifier:(id<ComposeAppKoin_coreQualifier> _Nullable)qualifier parameters:(ComposeAppKoin_coreParametersHolder * _Nullable)parameters __attribute__((swift_name("init(logger:scope:clazz:qualifier:parameters:)"))) __attribute__((objc_designated_initializer));
@property (readonly) id<ComposeAppKotlinKClass> clazz __attribute__((swift_name("clazz")));
@property (readonly) NSString *debugTag __attribute__((swift_name("debugTag")));
@property (readonly) ComposeAppKoin_coreLogger *logger __attribute__((swift_name("logger")));
@property (readonly) ComposeAppKoin_coreParametersHolder * _Nullable parameters __attribute__((swift_name("parameters")));
@property (readonly) id<ComposeAppKoin_coreQualifier> _Nullable qualifier __attribute__((swift_name("qualifier")));
@property (readonly) ComposeAppKoin_coreScope *scope __attribute__((swift_name("scope")));
@end

__attribute__((swift_name("Koin_coreKoinComponent")))
@protocol ComposeAppKoin_coreKoinComponent
@required
- (ComposeAppKoin_coreKoin *)getKoin __attribute__((swift_name("getKoin()")));
@end

__attribute__((swift_name("Koin_coreKoinScopeComponent")))
@protocol ComposeAppKoin_coreKoinScopeComponent <ComposeAppKoin_coreKoinComponent>
@required
@property (readonly) ComposeAppKoin_coreScope *scope __attribute__((swift_name("scope")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreExtensionManager")))
@interface ComposeAppKoin_coreExtensionManager : ComposeAppBase
- (instancetype)initWith_koin:(ComposeAppKoin_coreKoin *)_koin __attribute__((swift_name("init(_koin:)"))) __attribute__((objc_designated_initializer));
- (void)close __attribute__((swift_name("close()")));
- (id<ComposeAppKoin_coreKoinExtension>)getExtensionId:(NSString *)id __attribute__((swift_name("getExtension(id:)")));
- (id<ComposeAppKoin_coreKoinExtension> _Nullable)getExtensionOrNullId:(NSString *)id __attribute__((swift_name("getExtensionOrNull(id:)")));
- (void)registerExtensionId:(NSString *)id extension:(id<ComposeAppKoin_coreKoinExtension>)extension __attribute__((swift_name("registerExtension(id:extension:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreInstanceRegistry")))
@interface ComposeAppKoin_coreInstanceRegistry : ComposeAppBase
- (instancetype)initWith_koin:(ComposeAppKoin_coreKoin *)_koin __attribute__((swift_name("init(_koin:)"))) __attribute__((objc_designated_initializer));
- (void)saveMappingAllowOverride:(BOOL)allowOverride mapping:(NSString *)mapping factory:(ComposeAppKoin_coreInstanceFactory<id> *)factory logWarning:(BOOL)logWarning __attribute__((swift_name("saveMapping(allowOverride:mapping:factory:logWarning:)")));
- (int32_t)size __attribute__((swift_name("size()")));
@property (readonly) ComposeAppKoin_coreKoin *_koin __attribute__((swift_name("_koin")));
@property (readonly) NSDictionary<NSString *, ComposeAppKoin_coreInstanceFactory<id> *> *instances __attribute__((swift_name("instances")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_corePropertyRegistry")))
@interface ComposeAppKoin_corePropertyRegistry : ComposeAppBase
- (instancetype)initWith_koin:(ComposeAppKoin_coreKoin *)_koin __attribute__((swift_name("init(_koin:)"))) __attribute__((objc_designated_initializer));
- (void)close __attribute__((swift_name("close()")));
- (void)deletePropertyKey:(NSString *)key __attribute__((swift_name("deleteProperty(key:)")));
- (id _Nullable)getPropertyKey:(NSString *)key __attribute__((swift_name("getProperty(key:)")));
- (void)savePropertiesProperties:(NSDictionary<NSString *, id> *)properties __attribute__((swift_name("saveProperties(properties:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreScopeRegistry")))
@interface ComposeAppKoin_coreScopeRegistry : ComposeAppBase
- (instancetype)initWith_koin:(ComposeAppKoin_coreKoin *)_koin __attribute__((swift_name("init(_koin:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) ComposeAppKoin_coreScopeRegistryCompanion *companion __attribute__((swift_name("companion")));
- (void)loadScopesModules:(NSSet<ComposeAppKoin_coreModule *> *)modules __attribute__((swift_name("loadScopes(modules:)")));
@property (readonly) ComposeAppKoin_coreScope *rootScope __attribute__((swift_name("rootScope")));
@property (readonly) NSSet<id<ComposeAppKoin_coreQualifier>> *scopeDefinitions __attribute__((swift_name("scopeDefinitions")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinEnumCompanion")))
@interface ComposeAppKotlinEnumCompanion : ComposeAppBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppKotlinEnumCompanion *shared __attribute__((swift_name("shared")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreLevel")))
@interface ComposeAppKoin_coreLevel : ComposeAppKotlinEnum<ComposeAppKoin_coreLevel *>
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
@property (class, readonly) ComposeAppKoin_coreLevel *debug __attribute__((swift_name("debug")));
@property (class, readonly) ComposeAppKoin_coreLevel *info __attribute__((swift_name("info")));
@property (class, readonly) ComposeAppKoin_coreLevel *warning __attribute__((swift_name("warning")));
@property (class, readonly) ComposeAppKoin_coreLevel *error __attribute__((swift_name("error")));
@property (class, readonly) ComposeAppKoin_coreLevel *none __attribute__((swift_name("none")));
+ (ComposeAppKotlinArray<ComposeAppKoin_coreLevel *> *)values __attribute__((swift_name("values()")));
@property (class, readonly) NSArray<ComposeAppKoin_coreLevel *> *entries __attribute__((swift_name("entries")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreKind")))
@interface ComposeAppKoin_coreKind : ComposeAppKotlinEnum<ComposeAppKoin_coreKind *>
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
@property (class, readonly) ComposeAppKoin_coreKind *singleton __attribute__((swift_name("singleton")));
@property (class, readonly) ComposeAppKoin_coreKind *factory __attribute__((swift_name("factory")));
@property (class, readonly) ComposeAppKoin_coreKind *scoped __attribute__((swift_name("scoped")));
+ (ComposeAppKotlinArray<ComposeAppKoin_coreKind *> *)values __attribute__((swift_name("values()")));
@property (class, readonly) NSArray<ComposeAppKoin_coreKind *> *entries __attribute__((swift_name("entries")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreCallbacks")))
@interface ComposeAppKoin_coreCallbacks<T> : ComposeAppBase
- (instancetype)initWithOnClose:(void (^ _Nullable)(T _Nullable))onClose __attribute__((swift_name("init(onClose:)"))) __attribute__((objc_designated_initializer));
- (ComposeAppKoin_coreCallbacks<T> *)doCopyOnClose:(void (^ _Nullable)(T _Nullable))onClose __attribute__((swift_name("doCopy(onClose:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) void (^ _Nullable onClose)(T _Nullable) __attribute__((swift_name("onClose")));
@end

__attribute__((swift_name("Koin_coreKoinExtension")))
@protocol ComposeAppKoin_coreKoinExtension
@required
- (void)onClose __attribute__((swift_name("onClose()")));
- (void)onRegisterKoin:(ComposeAppKoin_coreKoin *)koin __attribute__((swift_name("onRegister(koin:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Koin_coreScopeRegistry.Companion")))
@interface ComposeAppKoin_coreScopeRegistryCompanion : ComposeAppBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) ComposeAppKoin_coreScopeRegistryCompanion *shared __attribute__((swift_name("shared")));
@end

#pragma pop_macro("_Nullable_result")
#pragma clang diagnostic pop
NS_ASSUME_NONNULL_END
