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
