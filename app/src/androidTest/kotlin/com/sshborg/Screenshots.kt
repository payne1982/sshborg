package com.sshborg

/**
 * Marks a class that produces store screenshots rather than checking anything.
 *
 * Those runs arrange a pretend set of hosts and keys and photograph the screens, which is slower
 * than a test and proves nothing, so the ordinary run leaves them out (`-e notAnnotation`) and
 * scripts/make-screenshots.sh asks for them on purpose.
 */
annotation class Screenshots
