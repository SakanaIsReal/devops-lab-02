package com.smartsplit.smartsplitback.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "group_members",
        uniqueConstraints = @UniqueConstraint(name="uk_group_user", columnNames={"group_id","user_id"}))
public class GroupMember {

    @EmbeddedId
    private GroupMemberId id = new GroupMemberId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("groupId")
    @JoinColumn(name = "group_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_group_members_group"))
    @OnDelete(action = OnDeleteAction.CASCADE)   // ลบ group -> สมาชิกหาย
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_group_members_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)   // ลบ user -> สมาชิกหาย
    private User user;

    public GroupMemberId getId(){ return id; }
    public void setId(GroupMemberId id){ this.id=id; }
    public Group getGroup(){ return group; }
    public void setGroup(Group group){ this.group=group; }
    public User getUser(){ return user; }
    public void setUser(User user){ this.user=user; }
}
