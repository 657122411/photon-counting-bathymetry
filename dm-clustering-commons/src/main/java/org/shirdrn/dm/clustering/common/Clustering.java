package org.shirdrn.dm.clustering.common;

import java.io.File;

public interface Clustering {

	void clustering(File... files);
	
	int getClusteredCount();
	
}