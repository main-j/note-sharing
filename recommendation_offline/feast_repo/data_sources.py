from feast import FileSource

user_source = FileSource(
    name="user_source",
    path="../data/feast/user_features.parquet",
    timestamp_field="event_timestamp",
)

item_source = FileSource(
    name="item_source",
    path="../data/feast/item_features.parquet",
    timestamp_field="event_timestamp",
)

author_source = FileSource(
    name="author_source",
    path="../data/feast/author_features.parquet",
    timestamp_field="event_timestamp",
)

cross_source = FileSource(
    name="cross_source",
    path="../data/feast/cross_features.parquet",
    timestamp_field="event_timestamp",
)
