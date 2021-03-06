/**
 * Author:  Alexander
 * Created: 26.03.2018
 */

create table akasha_task (
    id                      bigint                      not null auto_increment ,
    user_id                 bigint                      not null                ,
    task_name               varchar(128)                not null                ,
    task_type               int                         not null                ,
    task_status             int                         not null                ,
    task_priority           int                         not null                ,
    description             varchar(4096)               null default null       ,
    timezone                varchar(128)                not null                ,
    reminder_cron           varchar(128)                not null                ,
    last_reminder           timestamp                   not null                ,
    deadline                timestamp                   null default null       ,
    primary key ( id )                                                          ,
    foreign key ( user_id ) references akasha_user ( id )                       ,
    unique key uidx_user_task ( user_id, task_name )
)
;
