//
//  JamiBridgeWrapper.m
//  GetTogether
//
//  Mock implementation for testing Kotlin-Swift interop
//  Returns test data and simulates daemon behavior
//

#import "JamiBridgeWrapper.h"

// =============================================================================
// Data Class Implementations
// =============================================================================

@implementation JBContact
@end

@implementation JBTrustRequest
@end

@implementation JBConversationMember
@end

@implementation JBConversationRequest
@end

@implementation JBLookupResult
@end

@implementation JBFileTransferInfo
@end

@implementation JBSwarmMessage
@end

// =============================================================================
// JamiBridgeWrapper Implementation
// =============================================================================

@interface JamiBridgeWrapper ()

@property (nonatomic, assign) BOOL daemonRunning;
@property (nonatomic, copy) NSString *dataPath;
@property (nonatomic, strong) NSMutableArray<NSString *> *mockAccountIds;
@property (nonatomic, strong) NSMutableDictionary<NSString *, NSDictionary *> *mockAccountDetails;
@property (nonatomic, strong) NSMutableDictionary<NSString *, NSMutableArray<JBContact *> *> *mockContacts;
@property (nonatomic, strong) NSMutableDictionary<NSString *, NSMutableArray<NSString *> *> *mockConversations;
@property (nonatomic, strong) NSMutableDictionary<NSString *, NSMutableArray<JBSwarmMessage *> *> *mockMessages;
@property (nonatomic, assign) int messageLoadRequestId;

@end

@implementation JamiBridgeWrapper

// =============================================================================
// Singleton
// =============================================================================

+ (instancetype)shared {
    static JamiBridgeWrapper *instance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [[JamiBridgeWrapper alloc] init];
    });
    return instance;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _daemonRunning = NO;
        _mockAccountIds = [NSMutableArray array];
        _mockAccountDetails = [NSMutableDictionary dictionary];
        _mockContacts = [NSMutableDictionary dictionary];
        _mockConversations = [NSMutableDictionary dictionary];
        _mockMessages = [NSMutableDictionary dictionary];
        _messageLoadRequestId = 0;
    }
    return self;
}

// =============================================================================
// Helper Methods
// =============================================================================

- (NSString *)generateUUID {
    return [[NSUUID UUID] UUIDString];
}

- (int64_t)currentTimestamp {
    return (int64_t)([[NSDate date] timeIntervalSince1970] * 1000);
}

// =============================================================================
// Daemon Lifecycle
// =============================================================================

- (void)initDaemonWithDataPath:(NSString *)dataPath {
    NSLog(@"[JamiBridge] initDaemon with path: %@", dataPath);
    self.dataPath = dataPath;

    // Simulate async initialization
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.5 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        NSLog(@"[JamiBridge] Daemon initialized");
    });
}

- (void)startDaemon {
    NSLog(@"[JamiBridge] startDaemon");
    self.daemonRunning = YES;

    // Simulate daemon startup events
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.3 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        // Notify about existing accounts
        for (NSString *accountId in self.mockAccountIds) {
            if ([self.delegate respondsToSelector:@selector(onRegistrationStateChanged:state:code:detail:)]) {
                [self.delegate onRegistrationStateChanged:accountId
                                                    state:JBRegistrationStateRegistered
                                                     code:0
                                                   detail:@""];
            }
        }
    });
}

- (void)stopDaemon {
    NSLog(@"[JamiBridge] stopDaemon");
    self.daemonRunning = NO;
}

- (BOOL)isDaemonRunning {
    return self.daemonRunning;
}

// =============================================================================
// Account Management
// =============================================================================

- (NSString *)createAccountWithDisplayName:(NSString *)displayName password:(NSString *)password {
    NSLog(@"[JamiBridge] createAccount: %@", displayName);

    NSString *accountId = [self generateUUID];
    [self.mockAccountIds addObject:accountId];

    NSMutableDictionary *details = [NSMutableDictionary dictionary];
    details[@"Account.displayName"] = displayName;
    details[@"Account.alias"] = displayName;
    details[@"Account.type"] = @"JAMI";
    details[@"Account.enable"] = @"true";
    details[@"Account.registeredName"] = @"";
    details[@"Account.username"] = [NSString stringWithFormat:@"jami:%@", accountId];
    self.mockAccountDetails[accountId] = details;

    self.mockContacts[accountId] = [NSMutableArray array];
    self.mockConversations[accountId] = [NSMutableArray array];

    // Simulate registration events
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.5 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        if ([self.delegate respondsToSelector:@selector(onRegistrationStateChanged:state:code:detail:)]) {
            [self.delegate onRegistrationStateChanged:accountId
                                                state:JBRegistrationStateTrying
                                                 code:0
                                               detail:@""];
        }

        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            if ([self.delegate respondsToSelector:@selector(onRegistrationStateChanged:state:code:detail:)]) {
                [self.delegate onRegistrationStateChanged:accountId
                                                    state:JBRegistrationStateRegistered
                                                     code:0
                                                   detail:@""];
            }
        });
    });

    return accountId;
}

- (NSString *)importAccountFromArchive:(NSString *)archivePath password:(NSString *)password {
    NSLog(@"[JamiBridge] importAccount from: %@", archivePath);

    // Mock import - creates a new account
    return [self createAccountWithDisplayName:@"Imported Account" password:password];
}

- (BOOL)exportAccount:(NSString *)accountId
    toDestinationPath:(NSString *)destinationPath
         withPassword:(NSString *)password {
    NSLog(@"[JamiBridge] exportAccount: %@ to %@", accountId, destinationPath);
    return YES;
}

- (void)deleteAccount:(NSString *)accountId {
    NSLog(@"[JamiBridge] deleteAccount: %@", accountId);
    [self.mockAccountIds removeObject:accountId];
    [self.mockAccountDetails removeObjectForKey:accountId];
    [self.mockContacts removeObjectForKey:accountId];
    [self.mockConversations removeObjectForKey:accountId];
}

- (NSArray<NSString *> *)getAccountIds {
    return [self.mockAccountIds copy];
}

- (NSDictionary<NSString *, NSString *> *)getAccountDetails:(NSString *)accountId {
    return self.mockAccountDetails[accountId] ?: @{};
}

- (NSDictionary<NSString *, NSString *> *)getVolatileAccountDetails:(NSString *)accountId {
    if ([self.mockAccountIds containsObject:accountId]) {
        return @{
            @"Account.registrationStatus": @"REGISTERED",
            @"Account.registrationStateCode": @"0"
        };
    }
    return @{};
}

- (void)setAccountDetails:(NSString *)accountId
                  details:(NSDictionary<NSString *, NSString *> *)details {
    NSLog(@"[JamiBridge] setAccountDetails: %@", accountId);
    NSMutableDictionary *existing = [NSMutableDictionary dictionaryWithDictionary:self.mockAccountDetails[accountId] ?: @{}];
    [existing addEntriesFromDictionary:details];
    self.mockAccountDetails[accountId] = existing;

    if ([self.delegate respondsToSelector:@selector(onAccountDetailsChanged:details:)]) {
        [self.delegate onAccountDetailsChanged:accountId details:existing];
    }
}

- (void)setAccountActive:(NSString *)accountId active:(BOOL)active {
    NSLog(@"[JamiBridge] setAccountActive: %@ active: %d", accountId, active);

    JBRegistrationState state = active ? JBRegistrationStateRegistered : JBRegistrationStateUnregistered;
    if ([self.delegate respondsToSelector:@selector(onRegistrationStateChanged:state:code:detail:)]) {
        [self.delegate onRegistrationStateChanged:accountId state:state code:0 detail:@""];
    }
}

- (void)updateProfile:(NSString *)accountId
          displayName:(NSString *)displayName
           avatarPath:(nullable NSString *)avatarPath {
    NSLog(@"[JamiBridge] updateProfile: %@ name: %@", accountId, displayName);

    NSMutableDictionary *details = [NSMutableDictionary dictionaryWithDictionary:self.mockAccountDetails[accountId] ?: @{}];
    details[@"Account.displayName"] = displayName;
    if (avatarPath) {
        details[@"Account.avatar"] = avatarPath;
    }
    self.mockAccountDetails[accountId] = details;
}

- (BOOL)registerName:(NSString *)accountId name:(NSString *)name password:(NSString *)password {
    NSLog(@"[JamiBridge] registerName: %@ name: %@", accountId, name);

    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        if ([self.delegate respondsToSelector:@selector(onNameRegistrationEnded:state:name:)]) {
            [self.delegate onNameRegistrationEnded:accountId state:0 name:name];
        }
    });

    return YES;
}

- (nullable JBLookupResult *)lookupName:(NSString *)accountId name:(NSString *)name {
    NSLog(@"[JamiBridge] lookupName: %@", name);

    // Mock lookup - always return not found for now
    JBLookupResult *result = [[JBLookupResult alloc] init];
    result.name = name;
    result.address = @"";
    result.state = JBLookupStateNotFound;

    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.5 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        if ([self.delegate respondsToSelector:@selector(onRegisteredNameFound:state:address:name:)]) {
            [self.delegate onRegisteredNameFound:accountId
                                           state:JBLookupStateNotFound
                                         address:@""
                                            name:name];
        }
    });

    return result;
}

- (nullable JBLookupResult *)lookupAddress:(NSString *)accountId address:(NSString *)address {
    NSLog(@"[JamiBridge] lookupAddress: %@", address);

    JBLookupResult *result = [[JBLookupResult alloc] init];
    result.address = address;
    result.name = @"";
    result.state = JBLookupStateNotFound;

    return result;
}

// =============================================================================
// Contact Management
// =============================================================================

- (NSArray<JBContact *> *)getContacts:(NSString *)accountId {
    return self.mockContacts[accountId] ?: @[];
}

- (void)addContact:(NSString *)accountId uri:(NSString *)uri {
    NSLog(@"[JamiBridge] addContact: %@ uri: %@", accountId, uri);

    JBContact *contact = [[JBContact alloc] init];
    contact.uri = uri;
    contact.displayName = [NSString stringWithFormat:@"Contact %@", [uri substringToIndex:MIN(8, uri.length)]];
    contact.isConfirmed = NO;
    contact.isBanned = NO;

    NSMutableArray *contacts = self.mockContacts[accountId];
    if (!contacts) {
        contacts = [NSMutableArray array];
        self.mockContacts[accountId] = contacts;
    }
    [contacts addObject:contact];

    if ([self.delegate respondsToSelector:@selector(onContactAdded:uri:confirmed:)]) {
        [self.delegate onContactAdded:accountId uri:uri confirmed:NO];
    }
}

- (void)removeContact:(NSString *)accountId uri:(NSString *)uri ban:(BOOL)ban {
    NSLog(@"[JamiBridge] removeContact: %@ uri: %@ ban: %d", accountId, uri, ban);

    NSMutableArray *contacts = self.mockContacts[accountId];
    NSMutableIndexSet *toRemove = [NSMutableIndexSet indexSet];
    [contacts enumerateObjectsUsingBlock:^(JBContact *contact, NSUInteger idx, BOOL *stop) {
        if ([contact.uri isEqualToString:uri]) {
            [toRemove addIndex:idx];
        }
    }];
    [contacts removeObjectsAtIndexes:toRemove];

    if ([self.delegate respondsToSelector:@selector(onContactRemoved:uri:banned:)]) {
        [self.delegate onContactRemoved:accountId uri:uri banned:ban];
    }
}

- (NSDictionary<NSString *, NSString *> *)getContactDetails:(NSString *)accountId uri:(NSString *)uri {
    for (JBContact *contact in self.mockContacts[accountId]) {
        if ([contact.uri isEqualToString:uri]) {
            return @{
                @"uri": contact.uri,
                @"displayName": contact.displayName ?: @"",
                @"confirmed": contact.isConfirmed ? @"true" : @"false",
                @"banned": contact.isBanned ? @"true" : @"false"
            };
        }
    }
    return @{};
}

- (void)acceptTrustRequest:(NSString *)accountId uri:(NSString *)uri {
    NSLog(@"[JamiBridge] acceptTrustRequest: %@ uri: %@", accountId, uri);

    // Update contact as confirmed
    for (JBContact *contact in self.mockContacts[accountId]) {
        if ([contact.uri isEqualToString:uri]) {
            contact.isConfirmed = YES;
            break;
        }
    }

    if ([self.delegate respondsToSelector:@selector(onContactAdded:uri:confirmed:)]) {
        [self.delegate onContactAdded:accountId uri:uri confirmed:YES];
    }
}

- (void)discardTrustRequest:(NSString *)accountId uri:(NSString *)uri {
    NSLog(@"[JamiBridge] discardTrustRequest: %@ uri: %@", accountId, uri);
}

- (NSArray<JBTrustRequest *> *)getTrustRequests:(NSString *)accountId {
    // Return empty for mock
    return @[];
}

// =============================================================================
// Conversation Management
// =============================================================================

- (NSArray<NSString *> *)getConversations:(NSString *)accountId {
    return self.mockConversations[accountId] ?: @[];
}

- (NSString *)startConversation:(NSString *)accountId {
    NSLog(@"[JamiBridge] startConversation: %@", accountId);

    NSString *conversationId = [self generateUUID];

    NSMutableArray *conversations = self.mockConversations[accountId];
    if (!conversations) {
        conversations = [NSMutableArray array];
        self.mockConversations[accountId] = conversations;
    }
    [conversations addObject:conversationId];

    self.mockMessages[conversationId] = [NSMutableArray array];

    if ([self.delegate respondsToSelector:@selector(onConversationReady:conversationId:)]) {
        [self.delegate onConversationReady:accountId conversationId:conversationId];
    }

    return conversationId;
}

- (void)removeConversation:(NSString *)accountId conversationId:(NSString *)conversationId {
    NSLog(@"[JamiBridge] removeConversation: %@ conversationId: %@", accountId, conversationId);

    [self.mockConversations[accountId] removeObject:conversationId];
    [self.mockMessages removeObjectForKey:conversationId];

    if ([self.delegate respondsToSelector:@selector(onConversationRemoved:conversationId:)]) {
        [self.delegate onConversationRemoved:accountId conversationId:conversationId];
    }
}

- (NSDictionary<NSString *, NSString *> *)getConversationInfo:(NSString *)accountId
                                               conversationId:(NSString *)conversationId {
    return @{
        @"id": conversationId,
        @"title": @"Conversation",
        @"description": @"",
        @"mode": @"0"
    };
}

- (void)updateConversationInfo:(NSString *)accountId
                conversationId:(NSString *)conversationId
                          info:(NSDictionary<NSString *, NSString *> *)info {
    NSLog(@"[JamiBridge] updateConversationInfo: %@", conversationId);

    if ([self.delegate respondsToSelector:@selector(onConversationProfileUpdated:conversationId:profile:)]) {
        [self.delegate onConversationProfileUpdated:accountId conversationId:conversationId profile:info];
    }
}

- (NSArray<JBConversationMember *> *)getConversationMembers:(NSString *)accountId
                                             conversationId:(NSString *)conversationId {
    // Return self as the only member for now
    NSDictionary *details = self.mockAccountDetails[accountId];
    NSString *selfUri = details[@"Account.username"] ?: @"";

    JBConversationMember *member = [[JBConversationMember alloc] init];
    member.uri = selfUri;
    member.role = JBMemberRoleAdmin;

    return @[member];
}

- (void)addConversationMember:(NSString *)accountId
               conversationId:(NSString *)conversationId
                   contactUri:(NSString *)contactUri {
    NSLog(@"[JamiBridge] addConversationMember: %@ to %@", contactUri, conversationId);

    if ([self.delegate respondsToSelector:@selector(onConversationMemberEvent:conversationId:memberUri:event:)]) {
        [self.delegate onConversationMemberEvent:accountId
                                  conversationId:conversationId
                                       memberUri:contactUri
                                           event:JBMemberEventTypeJoin];
    }
}

- (void)removeConversationMember:(NSString *)accountId
                  conversationId:(NSString *)conversationId
                      contactUri:(NSString *)contactUri {
    NSLog(@"[JamiBridge] removeConversationMember: %@ from %@", contactUri, conversationId);

    if ([self.delegate respondsToSelector:@selector(onConversationMemberEvent:conversationId:memberUri:event:)]) {
        [self.delegate onConversationMemberEvent:accountId
                                  conversationId:conversationId
                                       memberUri:contactUri
                                           event:JBMemberEventTypeLeave];
    }
}

- (void)acceptConversationRequest:(NSString *)accountId conversationId:(NSString *)conversationId {
    NSLog(@"[JamiBridge] acceptConversationRequest: %@", conversationId);

    // Add to conversations
    NSMutableArray *conversations = self.mockConversations[accountId];
    if (!conversations) {
        conversations = [NSMutableArray array];
        self.mockConversations[accountId] = conversations;
    }
    [conversations addObject:conversationId];

    if ([self.delegate respondsToSelector:@selector(onConversationReady:conversationId:)]) {
        [self.delegate onConversationReady:accountId conversationId:conversationId];
    }
}

- (void)declineConversationRequest:(NSString *)accountId conversationId:(NSString *)conversationId {
    NSLog(@"[JamiBridge] declineConversationRequest: %@", conversationId);
}

- (NSArray<JBConversationRequest *> *)getConversationRequests:(NSString *)accountId {
    return @[];
}

// =============================================================================
// Messaging
// =============================================================================

- (NSString *)sendMessage:(NSString *)accountId
           conversationId:(NSString *)conversationId
                  message:(NSString *)message
                  replyTo:(nullable NSString *)replyTo {
    NSLog(@"[JamiBridge] sendMessage to %@: %@", conversationId, message);

    NSDictionary *details = self.mockAccountDetails[accountId];
    NSString *selfUri = details[@"Account.username"] ?: @"";

    JBSwarmMessage *msg = [[JBSwarmMessage alloc] init];
    msg.messageId = [self generateUUID];
    msg.type = @"text/plain";
    msg.author = selfUri;
    msg.body = @{@"body": message};
    msg.reactions = @[];
    msg.timestamp = [self currentTimestamp];
    msg.replyTo = replyTo;
    msg.status = @{};

    NSMutableArray *messages = self.mockMessages[conversationId];
    if (!messages) {
        messages = [NSMutableArray array];
        self.mockMessages[conversationId] = messages;
    }
    [messages addObject:msg];

    // Notify message sent
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.1 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        if ([self.delegate respondsToSelector:@selector(onMessageReceived:conversationId:message:)]) {
            [self.delegate onMessageReceived:accountId conversationId:conversationId message:msg];
        }
    });

    return msg.messageId;
}

- (int)loadConversationMessages:(NSString *)accountId
                 conversationId:(NSString *)conversationId
                    fromMessage:(NSString *)fromMessage
                          count:(int)count {
    NSLog(@"[JamiBridge] loadConversationMessages: %@ count: %d", conversationId, count);

    int requestId = ++self.messageLoadRequestId;

    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.2 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        NSArray *messages = self.mockMessages[conversationId] ?: @[];

        if ([self.delegate respondsToSelector:@selector(onMessagesLoaded:accountId:conversationId:messages:)]) {
            [self.delegate onMessagesLoaded:requestId
                                  accountId:accountId
                             conversationId:conversationId
                                   messages:messages];
        }
    });

    return requestId;
}

- (void)setIsComposing:(NSString *)accountId
        conversationId:(NSString *)conversationId
           isComposing:(BOOL)isComposing {
    NSLog(@"[JamiBridge] setIsComposing: %@ composing: %d", conversationId, isComposing);
}

- (void)setMessageDisplayed:(NSString *)accountId
             conversationId:(NSString *)conversationId
                  messageId:(NSString *)messageId {
    NSLog(@"[JamiBridge] setMessageDisplayed: %@ message: %@", conversationId, messageId);
}

// =============================================================================
// Calls
// =============================================================================

- (NSString *)placeCall:(NSString *)accountId uri:(NSString *)uri withVideo:(BOOL)withVideo {
    NSLog(@"[JamiBridge] placeCall to %@ video: %d", uri, withVideo);

    NSString *callId = [self generateUUID];

    // Simulate call state changes
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.3 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        if ([self.delegate respondsToSelector:@selector(onCallStateChanged:callId:state:code:)]) {
            [self.delegate onCallStateChanged:accountId callId:callId state:JBCallStateConnecting code:0];
        }

        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            if ([self.delegate respondsToSelector:@selector(onCallStateChanged:callId:state:code:)]) {
                [self.delegate onCallStateChanged:accountId callId:callId state:JBCallStateRinging code:0];
            }
        });
    });

    return callId;
}

- (void)acceptCall:(NSString *)accountId callId:(NSString *)callId withVideo:(BOOL)withVideo {
    NSLog(@"[JamiBridge] acceptCall: %@ video: %d", callId, withVideo);

    if ([self.delegate respondsToSelector:@selector(onCallStateChanged:callId:state:code:)]) {
        [self.delegate onCallStateChanged:accountId callId:callId state:JBCallStateCurrent code:0];
    }
}

- (void)refuseCall:(NSString *)accountId callId:(NSString *)callId {
    NSLog(@"[JamiBridge] refuseCall: %@", callId);

    if ([self.delegate respondsToSelector:@selector(onCallStateChanged:callId:state:code:)]) {
        [self.delegate onCallStateChanged:accountId callId:callId state:JBCallStateOver code:0];
    }
}

- (void)hangUp:(NSString *)accountId callId:(NSString *)callId {
    NSLog(@"[JamiBridge] hangUp: %@", callId);

    if ([self.delegate respondsToSelector:@selector(onCallStateChanged:callId:state:code:)]) {
        [self.delegate onCallStateChanged:accountId callId:callId state:JBCallStateOver code:0];
    }
}

- (void)holdCall:(NSString *)accountId callId:(NSString *)callId {
    NSLog(@"[JamiBridge] holdCall: %@", callId);

    if ([self.delegate respondsToSelector:@selector(onCallStateChanged:callId:state:code:)]) {
        [self.delegate onCallStateChanged:accountId callId:callId state:JBCallStateHold code:0];
    }
}

- (void)unholdCall:(NSString *)accountId callId:(NSString *)callId {
    NSLog(@"[JamiBridge] unholdCall: %@", callId);

    if ([self.delegate respondsToSelector:@selector(onCallStateChanged:callId:state:code:)]) {
        [self.delegate onCallStateChanged:accountId callId:callId state:JBCallStateCurrent code:0];
    }
}

- (void)muteAudio:(NSString *)accountId callId:(NSString *)callId muted:(BOOL)muted {
    NSLog(@"[JamiBridge] muteAudio: %@ muted: %d", callId, muted);

    if ([self.delegate respondsToSelector:@selector(onAudioMuted:muted:)]) {
        [self.delegate onAudioMuted:callId muted:muted];
    }
}

- (void)muteVideo:(NSString *)accountId callId:(NSString *)callId muted:(BOOL)muted {
    NSLog(@"[JamiBridge] muteVideo: %@ muted: %d", callId, muted);

    if ([self.delegate respondsToSelector:@selector(onVideoMuted:muted:)]) {
        [self.delegate onVideoMuted:callId muted:muted];
    }
}

- (NSDictionary<NSString *, NSString *> *)getCallDetails:(NSString *)accountId callId:(NSString *)callId {
    return @{
        @"CALL_STATE": @"CURRENT",
        @"PEER_NUMBER": @"",
        @"DISPLAY_NAME": @"",
        @"VIDEO_SOURCE": @"true"
    };
}

- (NSArray<NSString *> *)getActiveCalls:(NSString *)accountId {
    return @[];
}

- (void)switchCamera {
    NSLog(@"[JamiBridge] switchCamera");
}

- (void)switchAudioOutputUseSpeaker:(BOOL)useSpeaker {
    NSLog(@"[JamiBridge] switchAudioOutput speaker: %d", useSpeaker);
}

// =============================================================================
// Conference Calls
// =============================================================================

- (NSString *)createConference:(NSString *)accountId
               participantUris:(NSArray<NSString *> *)participantUris {
    NSLog(@"[JamiBridge] createConference with %lu participants", (unsigned long)participantUris.count);

    NSString *conferenceId = [self generateUUID];

    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.5 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        if ([self.delegate respondsToSelector:@selector(onConferenceCreated:conversationId:conferenceId:)]) {
            [self.delegate onConferenceCreated:accountId conversationId:@"" conferenceId:conferenceId];
        }
    });

    return conferenceId;
}

- (void)joinParticipant:(NSString *)accountId
                 callId:(NSString *)callId
              accountId2:(NSString *)accountId2
                 callId2:(NSString *)callId2 {
    NSLog(@"[JamiBridge] joinParticipant: %@ with %@", callId, callId2);
}

- (void)addParticipantToConference:(NSString *)accountId
                            callId:(NSString *)callId
               conferenceAccountId:(NSString *)conferenceAccountId
                      conferenceId:(NSString *)conferenceId {
    NSLog(@"[JamiBridge] addParticipantToConference: %@ conference: %@", callId, conferenceId);
}

- (void)hangUpConference:(NSString *)accountId conferenceId:(NSString *)conferenceId {
    NSLog(@"[JamiBridge] hangUpConference: %@", conferenceId);

    if ([self.delegate respondsToSelector:@selector(onConferenceRemoved:conferenceId:)]) {
        [self.delegate onConferenceRemoved:accountId conferenceId:conferenceId];
    }
}

- (NSDictionary<NSString *, NSString *> *)getConferenceDetails:(NSString *)accountId
                                                  conferenceId:(NSString *)conferenceId {
    return @{
        @"CONF_STATE": @"ACTIVE_ATTACHED",
        @"CONF_ID": conferenceId
    };
}

- (NSArray<NSString *> *)getConferenceParticipants:(NSString *)accountId
                                      conferenceId:(NSString *)conferenceId {
    return @[];
}

- (NSArray<NSDictionary<NSString *, NSString *> *> *)getConferenceInfos:(NSString *)accountId
                                                           conferenceId:(NSString *)conferenceId {
    return @[];
}

- (void)setConferenceLayout:(NSString *)accountId
               conferenceId:(NSString *)conferenceId
                     layout:(JBConferenceLayout)layout {
    NSLog(@"[JamiBridge] setConferenceLayout: %@ layout: %ld", conferenceId, (long)layout);
}

- (void)muteConferenceParticipant:(NSString *)accountId
                     conferenceId:(NSString *)conferenceId
                   participantUri:(NSString *)participantUri
                            muted:(BOOL)muted {
    NSLog(@"[JamiBridge] muteConferenceParticipant: %@ muted: %d", participantUri, muted);
}

- (void)hangUpConferenceParticipant:(NSString *)accountId
                       conferenceId:(NSString *)conferenceId
                     participantUri:(NSString *)participantUri
                           deviceId:(NSString *)deviceId {
    NSLog(@"[JamiBridge] hangUpConferenceParticipant: %@", participantUri);
}

// =============================================================================
// File Transfer
// =============================================================================

- (NSString *)sendFile:(NSString *)accountId
        conversationId:(NSString *)conversationId
              filePath:(NSString *)filePath
           displayName:(NSString *)displayName {
    NSLog(@"[JamiBridge] sendFile: %@ name: %@", filePath, displayName);
    return [self generateUUID];
}

- (void)acceptFileTransfer:(NSString *)accountId
            conversationId:(NSString *)conversationId
                    fileId:(NSString *)fileId
           destinationPath:(NSString *)destinationPath {
    NSLog(@"[JamiBridge] acceptFileTransfer: %@ to %@", fileId, destinationPath);
}

- (void)cancelFileTransfer:(NSString *)accountId
            conversationId:(NSString *)conversationId
                    fileId:(NSString *)fileId {
    NSLog(@"[JamiBridge] cancelFileTransfer: %@", fileId);
}

- (nullable JBFileTransferInfo *)getFileTransferInfo:(NSString *)accountId
                                      conversationId:(NSString *)conversationId
                                              fileId:(NSString *)fileId {
    return nil;
}

// =============================================================================
// Video
// =============================================================================

- (NSArray<NSString *> *)getVideoDevices {
    return @[@"front", @"back"];
}

- (NSString *)getCurrentVideoDevice {
    return @"front";
}

- (void)setVideoDevice:(NSString *)deviceId {
    NSLog(@"[JamiBridge] setVideoDevice: %@", deviceId);
}

- (void)startVideo {
    NSLog(@"[JamiBridge] startVideo");
}

- (void)stopVideo {
    NSLog(@"[JamiBridge] stopVideo");
}

// =============================================================================
// Audio Settings
// =============================================================================

- (NSArray<NSString *> *)getAudioOutputDevices {
    return @[@"speaker", @"earpiece", @"bluetooth"];
}

- (NSArray<NSString *> *)getAudioInputDevices {
    return @[@"microphone", @"bluetooth_mic"];
}

- (void)setAudioOutputDevice:(int)index {
    NSLog(@"[JamiBridge] setAudioOutputDevice: %d", index);
}

- (void)setAudioInputDevice:(int)index {
    NSLog(@"[JamiBridge] setAudioInputDevice: %d", index);
}

@end
