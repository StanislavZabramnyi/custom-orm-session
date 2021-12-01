package orm;

import annotation.Column;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class BaseEntity {
    @Column(name = "id")
    private Long id;
}
