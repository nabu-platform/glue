package be.nabu.glue.core.api;

/**
 * For collection handling purposes, iterables are too broad
 * Collections have the "property" that only the index and the values matter
 * For iterables that are not collections, this is not always true
 * 
 * For example an excel workbook can contain sheets, as such WorkBook is Iterable<Sheet>
 * However in this case we don't want to lose the actual workbook
 * 
 * If however you have a "straight" collection of sheets, for example List<Sheet> the collection is irrelevant
 * The collection API allowing for generalized collection access only deals with actual collections and throws away the container
 * 
 * Within glue, all iterables _are_ just series, glue simply uses the iterable concept for lazy evaluation purposes
 * For all intents and purposes, the iterables within glue _are_ collections
 */
public interface CollectionIterable<T> extends Iterable<T> {

}
