package tlc2.controlled.protocol;

import java.util.*;
import tlc2.tool.TLCState;

public interface StateAbstractor {
    List<TLCState> doAbstraction(List<TLCState> list);
}
