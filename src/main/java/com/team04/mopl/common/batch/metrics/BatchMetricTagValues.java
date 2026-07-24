package com.team04.mopl.common.batch.metrics;

import java.util.List;
import java.util.Set;

final class BatchMetricTagValues {

	static final List<String> RESULTS = List.of(
		"success",
		"failure",
		"stopped"
	);

	private static final Set<String> HARD_DELETE_STEPS = Set.of(
		"contentHardDeleteStep",
		"reviewHardDeleteStep",
		"playlistHardDeleteStep",
		"notificationHardDeleteStep"
	);

	static List<String> itemOperations(String step) {
		return List.of(
			"read",
			writeOperation(step),
			"filter",
			"skip"
		);
	}

	static String writeOperation(String step) {
		return HARD_DELETE_STEPS.contains(step) ? "delete" : "write";
	}

	private BatchMetricTagValues() {
	}
}
