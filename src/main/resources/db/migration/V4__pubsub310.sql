-- QUERY START:
drop procedure if exists TigPubSubCreateNode;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubRemoveNode;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubWriteItem;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubDeleteAllNodes;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubSetNodeAffiliation;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubSetNodeSubscription;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubRemoveService;
-- QUERY END:

delimiter //

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
		select node_id from tig_pubsub_nodes
			where name = _node_name and service_id = (select service_id from tig_pubsub_service_jids where service_jid = _service_id);
	END;

	START TRANSACTION;
	select TigPubSubEnsureServiceJid(_service_jid) into _service_id;
	select TigPubSubEnsureJid(_node_creator) into _node_creator_id;

	select node_id into _exists from tig_pubsub_nodes where name = _node_name and service_id = _service_id;
	if _exists is not null then
		select _exists as node_id;
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
create procedure TigPubSubRemoveService(_service_jid varchar(2049))
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
	delete from tig_pubsub_service_jids where service_id = _service_id;
	delete from tig_pubsub_affiliations where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = SHA1(_service_jid) and j.jid = _service_jid);
	delete from tig_pubsub_subscriptions where jid_id in (select j.jid_id from tig_pubsub_jids j where j.jid_sha1 = SHA1(_service_jid) and j.jid = _service_jid);
	COMMIT;
end //
-- QUERY END:

delimiter ;

-- QUERY START:
drop procedure if exists TigPubSubWriteItem;
-- QUERY END:

delimiter //

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
		values (_node_id, SHA1(_item_id), _item_id, UTC_TIMESTAMP(), UTC_TIMESTAMP(), _publisher_id, _item_data)
		on duplicate key update publisher_id = _publisher_id, data = _item_data, update_date = UTC_TIMESTAMP();
	COMMIT;
end //
-- QUERY END:

delimiter ;
