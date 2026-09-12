package com.microfi.transactions.domain;

/**
 * Which endpoint a collection request actually arrived on — determined by the server from the
 * route, never trusted from client-supplied request data, since the whole point is to gate
 * offline-originated records differently (see AgentDirectoryService#requireOnlineFirstCollectionCompletedForOffline)
 * and an app lying about its own origin would defeat that.
 */
public enum CollectionOrigin {
    /** POST /collections — a live, connected submission. */
    ONLINE,
    /** POST /collections/sync — replayed from the mobile app's local offline queue. */
    OFFLINE_SYNC
}
