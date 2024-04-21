package com.github.kvr000.zbyneklegal.format.collection;

import java.io.Closeable;
import java.util.Iterator;


public interface CloseableIterator<T> extends Iterator<T>, Closeable
{
}
