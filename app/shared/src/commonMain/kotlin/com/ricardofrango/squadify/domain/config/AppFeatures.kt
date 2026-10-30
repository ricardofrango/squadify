package com.ricardofrango.squadify.domain.config

/**
 * What an app has, for the screens both apps share (TECHNICAL_DECISIONS §72). Each app binds its own in
 * its Koin module: the clubs app has everything; the coach app, for now, none of these.
 */
data class AppFeatures(
    // Training: plans, sessions, ratings and training stats. Without it, no Training tab and no
    // training calls.
    val training: Boolean,
    // Picking a season on the create screens. Without it the screens show none, and the server uses the
    // season running today.
    val seasons: Boolean,
    // Photos and logos, which need the server's image storage.
    val photos: Boolean,
    // Staff come from the club's members: a staff name opens the member's page, and Add staff picks from
    // the members.
    val clubMembers: Boolean
)
