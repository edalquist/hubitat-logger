package org.dalquist.hubitat.logging;

import java.util.Collection;

import com.google.common.collect.ImmutableList;

final class CompositeAutoCloseable implements AutoCloseable {
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private final ImmutableList<AutoCloseable> closables;

    private CompositeAutoCloseable(Collection<AutoCloseable> closables) {
        this.closables = ImmutableList.copyOf(closables);
    }

    static CompositeAutoCloseable fromSuppliers(Collection<ThrowingSupplier<AutoCloseable>> closablesSuppliers)
            throws Exception {
        ImmutableList.Builder<AutoCloseable> closables = ImmutableList.builder();
        for (ThrowingSupplier<AutoCloseable> supplier : closablesSuppliers) {
            try {
                closables.add(supplier.get());
            } catch (Exception e) {
                close(e, closables.build());
                throw e;
            }
        }

        return new CompositeAutoCloseable(closables.build());
    }

    @Override
    public void close() throws Exception {
        close(null, closables);
    }

    private static void close(Exception e, ImmutableList<AutoCloseable> closables2) throws Exception {
        for (AutoCloseable closable : closables2) {
            try {
                closable.close();
            } catch (Exception eSub) {
                if (e == null) {
                    e = eSub;
                } else {
                    e.addSuppressed(eSub);
                }
            }
        }
        if (e != null) {
            throw e;
        }
    }
}
