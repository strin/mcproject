#include "Fingerprint.h"

JNIEXPORT jlong JNICALL Java_Fingerprint_getFingerprintCXX
(JNIEnv *, jobject, jlong _iteration, jlong _startSeed) {
	long iteration = (long)_iteration;
	long seed = (long)_startSeed;
	for(int i = 0; i < iteration; i++) {
		seed = seed*25214903917L+11L;
	}
	return (seed >> 12) & 0xFFFFL;
}
