-- QUERY START:
drop function if exists TigPubSubEnsureServiceJid;
-- QUERY END:
-- QUERY START:
drop function if exists TigPubSubEnsureJid;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubCreateNode;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubRemoveNode;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetItem;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubWriteItem;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubDeleteItem;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetNodeId;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetNodeItemsIdsSince;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetAllNodes;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetNodeItemsIds;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetRootNodes;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetChildNodes;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubDeleteAllNodes;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubSetNodeConfiguration;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubSetNodeAffiliation;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetNodeConfiguration;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetNodeAffiliations;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetNodeSubscriptions;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubSetNodeSubscription;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubDeleteNodeSubscription;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetUserAffiliations;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetUserSubscriptions;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubGetNodeItemsMeta;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubFixNode;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigPubSubFixItem;
-- QUERY END:


-- QUERY START:
create table if not exists tig_pubsub_service_jids (
	service_id bigint not null auto_increment,
	service_jid varchar(2049) not null,
	service_jid_sha1 char(40) not null,

	primary key ( service_id ),
	index using hash ( service_jid(255) ),
	unique index using hash ( service_jid_sha1(40) )
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- QUERY START:
create table if not exists tig_pubsub_jids (
	jid_id bigint not null auto_increment,
	jid varchar(2049) not null,
	jid_sha1 char(40) not null,

	primary key ( jid_id ),
	index using hash ( jid(255) ),
	unique index using hash ( jid_sha1(40) )
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- QUERY START:
create table if not exists tig_pubsub_nodes (
	node_id bigint not null auto_increment,
	service_id bigint not null,
	name varchar(1024) not null,
	name_sha1 char(40) not null,
	type int not null,
	title varchar(1000),
	description mediumtext,
	creator_id bigint,
	creation_date datetime,
	configuration mediumtext,
	collection_id bigint,

	primary key ( node_id ),
	index using hash ( service_id ),
	index using hash ( name(255) ),
	index using hash ( service_id, name(255) ),
	unique index using hash ( service_id, name_sha1(40) ),
	index using hash ( collection_id ),

	constraint
		foreign key ( service_id )
		references tig_pubsub_service_jids ( service_id )
		match full
		on delete cascade,
	constraint
		foreign key ( creator_id )
		references tig_pubsub_jids ( jid_id )
		match full,
	constraint
		foreign key ( collection_id )
		references tig_pubsub_nodes ( node_id )
		match full
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- QUERY START:
create table if not exists tig_pubsub_affiliations (
	node_id bigint not null,
	jid_id bigint not null,
	affiliation varchar(20) not null,

	primary key ( node_id, jid_id ),
	index using hash ( node_id ),
	index using hash ( jid_id ),
	unique index using hash ( node_id, jid_id ),

	constraint
		foreign key ( node_id )
		references tig_pubsub_nodes ( node_id )
		match full
		on delete cascade,
	constraint
		foreign key ( jid_id )
		references tig_pubsub_jids ( jid_id )
		match full
		on delete cascade
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- QUERY START:
create table if not exists tig_pubsub_subscriptions (
	node_id bigint not null references tig_pubsub_nodes ( node_id ),
	jid_id bigint not null references tig_pubsub_jids ( jid_id ),
	subscription varchar(20) not null,
	subscription_id varchar(40) not null,

	primary key ( node_id, jid_id ),
	index using hash ( node_id ),
	index using hash ( jid_id ),
	unique index using hash ( node_id, jid_id ),

	constraint
		foreign key ( node_id )
		references tig_pubsub_nodes ( node_id )
		match full
		on delete cascade,
	constraint
		foreign key ( jid_id )
		references tig_pubsub_jids ( jid_id )
		match full
		on delete cascade
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- QUERY START:
create table if not exists tig_pubsub_items (
	node_id bigint not null,
	id varchar(1024) not null,
	id_sha1 char(40) not null,
	creation_date datetime,
	publisher_id bigint,
	update_date datetime,
	data mediumtext,

	primary key ( node_id, id_sha1(40) ),
	index using hash ( node_id, id(255) ),
	index using hash ( node_id ),

	constraint
		foreign key ( node_id )
		references tig_pubsub_nodes ( node_id )
		match full
		on delete cascade,
	constraint
		foreign key ( publisher_id )
		references tig_pubsub_jids ( jid_id )
		match full
		on delete cascade
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

delimiter //

-- QUERY START:
create function TigPubSubEnsureServiceJid(_service_jid varchar(2049)) returns bigint DETERMINISTIC
begin
	declare _service_id bigint;
	declare _service_jid_sha1 char(40);

	select SHA1(_service_jid) into _service_jid_sha1;
	select service_id into _service_id from tig_pubsub_service_jids where service_jid_sha1 = _service_jid_sha1;
	if _service_id is null then
		insert into tig_pubsub_service_jids (service_jid, service_jid_sha1)
			values (_service_jid, _service_jid_sha1);
		select LAST_INSERT_ID() into _service_id;
	end if;

	return (_service_id);
end //
-- QUERY END:

-- QUERY START:
create function TigPubSubEnsureJid(_jid varchar(2049)) returns bigint DETERMINISTIC
begin
	declare _jid_id bigint;
	declare _jid_sha1 char(40);

	select SHA1(_jid) into _jid_sha1;
	select jid_id into _jid_id from tig_pubsub_jids where jid_sha1 = _jid_sha1;
	if _jid_id is null then
		insert into tig_pubsub_jids (jid, jid_sha1)
			values (_jid, _jid_sha1)
			on duplicate key update jid_id = LAST_INSERT_ID(jid_id);
		select LAST_INSERT_ID() into _jid_id;
	end if;

	return (_jid_id);
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubCreateNode(_service_jid varchar(2049), _node_name varchar(1024), _node_type int,
	_node_creator varchar(2049), _node_conf text, _collection_id bigint)
begin
	declare _service_id bigint;
	declare _node_creator_id bigint;
	declare _node_id bigint;
	declare _exists int;

	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
		select node_id from tig_pubsub_nodes where node_id = _node_id and service_id = _service_id;
	END;

	START TRANSACTION;
	select TigPubSubEnsureServiceJid(_service_jid) into _service_id;
	select TigPubSubEnsureJid(_node_creator) into _node_creator_id;

	select node_id into _exists from tig_pubsub_nodes where node_id = _node_id and service_id = _service_id;
	if _exists is not null then
		select _node_id as node_id;
	else
		insert into tig_pubsub_nodes (service_id,name,name_sha1,`type`,creator_id,configuration,collection_id)
			values (_service_id, _node_name, SHA1(_node_name), _node_type, _node_creator_id, _node_conf, _collection_id);
		select LAST_INSERT_ID() into _node_id;
		select _node_id as node_id;
	end if;

	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubRemoveNode(_node_id bigint)
begin
	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

	START TRANSACTION;
	delete from tig_pubsub_items where node_id = _node_id;
	delete from tig_pubsub_subscriptions where node_id = _node_id;
	delete from tig_pubsub_affiliations where node_id = _node_id;
	delete from tig_pubsub_nodes where node_id = _node_id;
	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetItem(_node_id bigint, _item_id varchar(1024))
begin
	select `data`, p.jid, creation_date, update_date
		from tig_pubsub_items pi
		inner join tig_pubsub_jids p on p.jid_id = pi.publisher_id
		where node_id = _node_id and id_sha1 = SHA1(_item_id) and id = _item_id;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubWriteItem(_node_id bigint, _item_id varchar(1024), _publisher varchar(2049),
	 _item_data mediumtext)
begin
	declare _publisher_id bigint;
	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

	START TRANSACTION;

	select TigPubSubEnsureJid(_publisher) into _publisher_id;
	insert into tig_pubsub_items (node_id, id_sha1, id, creation_date, update_date, publisher_id, data)
		values (_node_id, SHA1(_item_id), _item_id, now(), now(), _publisher_id, _item_data)
		on duplicate key update publisher_id = _publisher_id, data = _item_data, update_date = now();
	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubDeleteItem(_node_id bigint, _item_id varchar(1024))
begin
	delete from tig_pubsub_items where node_id = _node_id and id_sha1 = SHA1(_item_id) and id = _item_id;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeId(_service_jid varchar(2049), _node_name varchar(1024))
begin
	select n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where sj.service_jid_sha1 = SHA1(_service_jid) and n.name_sha1 = SHA1(_node_name)
			and sj.service_jid = _service_jid and n.name = _node_name;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeItemsIds(_node_id bigint)
begin
	select id from tig_pubsub_items where node_id = _node_id order by creation_date;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeItemsIdsSince(_node_id bigint,_since datetime)
begin
	select id from tig_pubsub_items where node_id = _node_id
		and creation_date >= _since order by creation_date;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetAllNodes(_service_jid varchar(2049))
begin
	select n.name, n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where sj.service_jid_sha1 = SHA1(_service_jid) and sj.service_jid = _service_jid;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetRootNodes(_service_jid varchar(2049))
begin
	select n.name, n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		where sj.service_jid_sha1 = SHA1(_service_jid) and sj.service_jid = _service_jid
			and n.collection_id is null;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetChildNodes(_service_jid varchar(2049),_node_name varchar(1024))
begin
	select n.name, n.node_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		inner join tig_pubsub_nodes p on p.node_id = n.collection_id and p.service_id = sj.service_id
		where sj.service_jid_sha1 = SHA1(_service_jid) and p.name_sha1 = SHA1(_node_name)
			and sj.service_jid = _service_jid and p.name = _node_name;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubDeleteAllNodes(_service_jid varchar(2049))
begin
	declare _service_id bigint;
	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

	START TRANSACTION;

	select service_id into _service_id from tig_pubsub_service_jids
		where service_jid_sha1 = SHA1(_service_jid) and service_jid = _service_jid;
	delete from tig_pubsub_items where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = _service_id);
	delete from tig_pubsub_affiliations where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = _service_id);
	delete from tig_pubsub_subscriptions where node_id in (
		select n.node_id from tig_pubsub_nodes n where n.service_id = _service_id);
	delete from tig_pubsub_nodes where service_id = _service_id;

	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubSetNodeConfiguration(_node_id bigint, _node_conf mediumtext, _collection_id bigint)
begin
	update tig_pubsub_nodes set configuration = _node_conf, collection_id = _collection_id where node_id = _node_id;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubSetNodeAffiliation(_node_id bigint, _jid varchar(2049), _affil varchar(20))
begin
	declare _jid_id bigint;
	declare _exists int;

	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

	START TRANSACTION;

	select jid_id into _jid_id from tig_pubsub_jids where jid_sha1 = SHA1(_jid) and jid = _jid;
	if _jid_id is not null then
		select 1 into _exists from tig_pubsub_affiliations pa where pa.node_id = _node_id and pa.jid_id = _jid_id;
	end if;
	if _affil != 'none' then
		if _jid_id is null then
			select TigPubSubEnsureJid(_jid) into _jid_id;
		end if;
		if _exists is not null then
			update tig_pubsub_affiliations set affiliation = _affil where node_id = _node_id and jid_id = _jid_id;
		else
			insert into tig_pubsub_affiliations (node_id, jid_id, affiliation)
				values (_node_id, _jid_id, _affil);
		end if;
	else
		if _exists is not null then
			delete from tig_pubsub_affiliations where node_id = _node_id and jid_id = _jid_id;
		end if;
	end if;

	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeConfiguration(_node_id bigint)
begin
  select configuration from tig_pubsub_nodes where node_id = _node_id;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeAffiliations(_node_id bigint)
begin
	select pj.jid, pa.affiliation from tig_pubsub_affiliations pa
		inner join tig_pubsub_jids pj on pa.jid_id = pj.jid_id
		where pa.node_id = _node_id;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeSubscriptions(_node_id bigint)
begin
	select pj.jid, ps.subscription, ps.subscription_id
		from tig_pubsub_subscriptions ps
		inner join tig_pubsub_jids pj on ps.jid_id = pj.jid_id
		where ps.node_id = _node_id;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubSetNodeSubscription(_node_id bigint, _jid varchar(2049),
	_subscr varchar(20), _subscr_id varchar(40))
begin
	declare _jid_id bigint;
	declare _exists int;

	DECLARE exit handler for sqlexception
		BEGIN
			-- ERROR
		ROLLBACK;
	END;

	START TRANSACTION;

	select TigPubSubEnsureJid(_jid) into _jid_id;
	select 1 into _exists from tig_pubsub_subscriptions where node_id = _node_id and jid_id = _jid_id;
	if _exists is not null then
		update tig_pubsub_subscriptions set subscription = _subscr
			where node_id = _node_id and jid_id = _jid_id;
	else
		insert into tig_pubsub_subscriptions (node_id,jid_id,subscription,subscription_id)
			values (_node_id,_jid_id,_subscr,_subscr_id);
	end if;

	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubDeleteNodeSubscription(_node_id bigint, _jid varchar(2049))
begin
	delete from tig_pubsub_subscriptions where node_id = _node_id and jid_id = (
		select jid_id from tig_pubsub_jids where jid_sha1 = SHA1(_jid) and jid = _jid
	);
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetUserAffiliations(_service_jid varchar(2049), _jid varchar(2049))
begin
	select n.name, pa.affiliation from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id
		inner join tig_pubsub_affiliations pa on pa.node_id = n.node_id
		inner join tig_pubsub_jids pj on pj.jid_id = pa.jid_id
		where pj.jid_sha1 = SHA1(_jid) and sj.service_jid_sha1 = SHA1(_service_jid)
			and pj.jid = _jid and sj.service_jid = _service_jid;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetUserSubscriptions(_service_jid varchar(2049), _jid varchar(2049))
begin
	select n.name, ps.subscription, ps.subscription_id from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on sj.service_id = n.service_id
		inner join tig_pubsub_subscriptions ps on ps.node_id = n.node_id
		inner join tig_pubsub_jids pj on pj.jid_id = ps.jid_id
		where pj.jid_sha1 = SHA1(_jid) and sj.service_jid_sha1 = SHA1(_service_jid)
			and pj.jid = _jid and sj.service_jid = _service_jid;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeItemsMeta(_node_id bigint)
begin
	select id, creation_date, update_date from tig_pubsub_items where node_id = _node_id order by creation_date;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubFixNode(_node_id bigint, _node_creation_date datetime)
begin
	update tig_pubsub_nodes set creation_date = _node_creation_date where node_id = _node_id;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubFixItem(_node_id bigint, _item_id varchar(1024),
	_creation_date datetime, _update_date datetime)
begin
	update tig_pubsub_items set creation_date = _creation_date, update_date = _update_date
		where node_id = _node_id and id_sha1 = SHA1(_item_id) and id = _item_id;
end //
-- QUERY END:

delimiter ;
