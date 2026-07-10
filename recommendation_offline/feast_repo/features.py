from datetime import timedelta

from feast import Entity, FeatureService, FeatureView, Field, FileSource, ValueType
from feast.types import Float32, Int64, String

user = Entity(name="user_id", join_keys=["user_id"], value_type=ValueType.INT64)
item = Entity(name="item_id", join_keys=["item_id"], value_type=ValueType.INT64)
author = Entity(name="author_id", join_keys=["author_id"], value_type=ValueType.INT64)
user_item = Entity(name="user_item_key", join_keys=["user_item_key"], value_type=ValueType.STRING)

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

user_features = FeatureView(
    name="user_features",
    entities=[user],
    ttl=timedelta(days=14),
    schema=[
        Field(name="top_tag_1", dtype=String),
        Field(name="top_tag_2", dtype=String),
        Field(name="recent_search_count", dtype=Int64),
        Field(name="active_days_7d", dtype=Int64),
    ],
    source=user_source,
)

item_features = FeatureView(
    name="item_features",
    entities=[item],
    ttl=timedelta(days=7),
    schema=[
        Field(name="views", dtype=Int64),
        Field(name="likes", dtype=Int64),
        Field(name="favorites", dtype=Int64),
        Field(name="comments", dtype=Int64),
        Field(name="hot_score", dtype=Float32),
    ],
    source=item_source,
)

author_features = FeatureView(
    name="author_features",
    entities=[author],
    ttl=timedelta(days=14),
    schema=[
        Field(name="author_quality_score", dtype=Float32),
        Field(name="author_recent_posts", dtype=Int64),
    ],
    source=author_source,
)

cross_features = FeatureView(
    name="cross_features",
    entities=[user_item],
    ttl=timedelta(days=7),
    schema=[
        Field(name="tag_match_score", dtype=Float32),
        Field(name="is_followee_author", dtype=Int64),
    ],
    source=cross_source,
)

user_feature_service = FeatureService(
    name="user_features",
    features=[user_features],
)

item_feature_service = FeatureService(
    name="item_features",
    features=[item_features, author_features, cross_features],
)
