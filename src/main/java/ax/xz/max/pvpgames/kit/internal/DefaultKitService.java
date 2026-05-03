package ax.xz.max.pvpgames.kit.internal;

import ax.xz.max.pvpgames.kit.InventorySnapshot;
import ax.xz.max.pvpgames.kit.Kit;
import ax.xz.max.pvpgames.kit.KitName;
import ax.xz.max.pvpgames.kit.KitPersistenceException;
import ax.xz.max.pvpgames.kit.KitRepository;
import ax.xz.max.pvpgames.kit.KitResult;
import ax.xz.max.pvpgames.kit.KitService;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link KitService} implementation.
 *
 * <p>Validation, snapshot capture, and inventory mutation happen on the calling
 * thread (which must be the server main thread for any method that touches a
 * {@link Player}). Persistence is delegated to {@link KitRepository}, whose
 * implementation handles caching and durability.
 */
public final class DefaultKitService implements KitService {

    private final KitRepository repository;
    private final Clock clock;

    public DefaultKitService(KitRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public KitResult.CreateResult create(Player owner, String rawName) {
        Objects.requireNonNull(owner, "owner");

        KitName.Result parsed = KitName.tryParse(rawName);
        if (parsed instanceof KitName.Result.Invalid(String reason)) {
            return new KitResult.CreateResult.InvalidName(reason);
        }
        KitName name = ((KitName.Result.Valid) parsed).name();

        InventorySnapshot snapshot = InventorySnapshot.captureFrom(owner);
        if (snapshot.isEmpty()) {
            return new KitResult.CreateResult.EmptyInventory();
        }

        Kit kit = new Kit(name, snapshot, clock.instant(), owner.getUniqueId());
        try {
            boolean replaced = repository.save(kit);
            return new KitResult.CreateResult.Created(kit, replaced);
        } catch (KitPersistenceException ex) {
            return new KitResult.CreateResult.IoError(ex.getMessage());
        }
    }

    @Override
    public KitResult.LoadResult load(Player target, String rawName) {
        Objects.requireNonNull(target, "target");

        KitName.Result parsed = KitName.tryParse(rawName);
        if (parsed instanceof KitName.Result.Invalid(String reason)) {
            return new KitResult.LoadResult.InvalidName(reason);
        }
        KitName name = ((KitName.Result.Valid) parsed).name();

        Optional<Kit> kit = repository.find(name);
        if (kit.isEmpty()) {
            return new KitResult.LoadResult.NotFound(rawName);
        }

        target.closeInventory();
        kit.get().contents().applyTo(target);
        return new KitResult.LoadResult.Loaded(kit.get());
    }

    @Override
    public KitResult.DeleteResult delete(String rawName) {
        KitName.Result parsed = KitName.tryParse(rawName);
        if (parsed instanceof KitName.Result.Invalid(String reason)) {
            return new KitResult.DeleteResult.InvalidName(reason);
        }
        KitName name = ((KitName.Result.Valid) parsed).name();

        try {
            boolean existed = repository.delete(name);
            return existed
                    ? new KitResult.DeleteResult.Deleted(name)
                    : new KitResult.DeleteResult.NotFound(rawName);
        } catch (KitPersistenceException ex) {
            return new KitResult.DeleteResult.IoError(ex.getMessage());
        }
    }

    @Override
    public List<KitName> listNames() {
        return repository.all().stream()
                .map(Kit::name)
                .sorted(Comparator.comparing(KitName::value))
                .toList();
    }

    @Override
    public Optional<Kit> find(String rawName) {
        return switch (KitName.tryParse(rawName)) {
            case KitName.Result.Valid v -> repository.find(v.name());
            case KitName.Result.Invalid ignored -> Optional.empty();
        };
    }
}
