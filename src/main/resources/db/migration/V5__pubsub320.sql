-- QUERY START:
drop procedure if exists TigPubSubCreateNode;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigPubSubGetNodeMeta;
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
		insert into tig_pubsub_nodes (service_id,name,name_sha1,`type`,creator_id, creation_date, configuration,collection_id)
			values (_service_id, _node_name, SHA1(_node_name), _node_type, _node_creator_id, now(), _node_conf, _collection_id);
		select LAST_INSERT_ID() into _node_id;
		select _node_id as node_id;
	end if;

	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure TigPubSubGetNodeMeta(_service_jid varchar(2049), _node_name varchar(1024))
begin
	select n.node_id, n.configuration, cj.jid, n.creation_date
	from tig_pubsub_nodes n
		inner join tig_pubsub_service_jids sj on n.service_id = sj.service_id
		inner join tig_pubsub_jids cj on cj.jid_id = n.creator_id
		where sj.service_jid_sha1 = SHA1(_service_jid) and n.name_sha1 = SHA1(_node_name)
			and sj.service_jid = _service_jid and n.name = _node_name;
end //
-- QUERY END:

delimiter ;
