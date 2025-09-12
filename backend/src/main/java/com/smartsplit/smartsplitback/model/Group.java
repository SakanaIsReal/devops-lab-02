package com.smartsplit.smartsplitback.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Objects;

@Entity
@Table(name = "groups_tbl")
public class Group {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    // FK -> users.user_id   (ลบ user แล้ว group จะถูกลบตาม)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_groups_owner"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User owner;

    @Column(length = 120, nullable = false)
    private String name;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    // getters/setters/equals/hashCode
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public User getOwner() { return owner; } public void setOwner(User owner) { this.owner = owner; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getCoverImageUrl() { return coverImageUrl; } public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }

    @Override public boolean equals(Object o){ return o instanceof Group g && Objects.equals(id,g.id); }
    @Override public int hashCode(){ return Objects.hashCode(id); }
}
