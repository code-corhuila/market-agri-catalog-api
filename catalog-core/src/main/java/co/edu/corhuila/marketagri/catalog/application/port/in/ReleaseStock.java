package co.edu.corhuila.marketagri.catalog.application.port.in;

import java.util.UUID;

/** The saga's compensation gives the stock back (I-04): once, and silently for anything unknown. */
public interface ReleaseStock {

    void release(UUID transactionId);
}
