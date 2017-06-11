CREATE TABLE `messages` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `uid` bigint(20) unsigned NOT NULL,
  `stanza` mediumtext NOT NULL,
  `timestamp` datetime NOT NULL,
  `expired` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `uid` (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Offline message storage';

ALTER TABLE `messages`
  ADD CONSTRAINT `messages_ibfk_2` FOREIGN KEY (`uid`) REFERENCES `tig_users` (`uid`) ON DELETE CASCADE,
  ADD CONSTRAINT `messages_ibfk_1` FOREIGN KEY (`uid`) REFERENCES `tig_users` (`uid`) ON DELETE CASCADE;

CREATE TABLE `servers` (
  `fingerprint` char(40) NOT NULL COMMENT 'Server key fingerprint',
  `host` varchar(100) NOT NULL COMMENT 'Server address',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Server enabled in the network',
  PRIMARY KEY (`fingerprint`)
) ENGINE=MyISAM DEFAULT CHARSET=ascii COMMENT='Servers';

CREATE TABLE `push` (
  `user_id` varchar(300) CHARACTER SET utf8 NOT NULL COMMENT 'User ID',
  `provider` varchar(20) NOT NULL COMMENT 'Push provider',
  `reg_id` varchar(1000) NOT NULL COMMENT 'Registration ID'
) ENGINE=MyISAM DEFAULT CHARSET=ascii COMMENT='Verification codes';

ALTER TABLE `push`
 ADD PRIMARY KEY (`user_id`,`provider`);

CREATE TABLE `validations` (
  `user_id` varchar(300) CHARACTER SET utf8 NOT NULL COMMENT 'User ID',
  `code` char(6) NOT NULL COMMENT 'Verification code',
  `timestamp` datetime DEFAULT NULL COMMENT 'Validation code timestamp'
) ENGINE=MyISAM DEFAULT CHARSET=ascii COMMENT='Verification codes';

ALTER TABLE `validations`
 ADD PRIMARY KEY (`user_id`), ADD UNIQUE KEY `code` (`code`);
