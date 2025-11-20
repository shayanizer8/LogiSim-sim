package org.logisim.business;

import static org.logisim.business.ModelContracts.ComponentId;

/** Simple connector implementation carrying routing info. */
public class BasicConnector implements ModelContracts.Connector {
    private final String id;
    private final ComponentId sourceComponentId;
    private final int sourcePortIndex;
    private final ComponentId sinkComponentId;
    private final int sinkPortIndex;
    private final String color;

    public BasicConnector(String id, ComponentId sourceComponentId, int sourcePortIndex,
                          ComponentId sinkComponentId, int sinkPortIndex, String color) {
        this.id = id;
        this.sourceComponentId = sourceComponentId;
        this.sourcePortIndex = sourcePortIndex;
        this.sinkComponentId = sinkComponentId;
        this.sinkPortIndex = sinkPortIndex;
        this.color = color == null ? "black" : color;
    }

    @Override public String getId() { return id; }
    @Override public ComponentId getSourceComponentId() { return sourceComponentId; }
    @Override public int getSourcePortIndex() { return sourcePortIndex; }
    @Override public ComponentId getSinkComponentId() { return sinkComponentId; }
    @Override public int getSinkPortIndex() { return sinkPortIndex; }
    @Override public String getColor() { return color; }
}
