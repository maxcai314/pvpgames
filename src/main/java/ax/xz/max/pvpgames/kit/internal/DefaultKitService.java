package ax.xz.max.pvpgames.kit.internal;

import ax.xz.max.async.Result;
import ax.xz.max.pvpgames.kit.InventorySnapshot;
import ax.xz.max.pvpgames.kit.Kit;
import ax.xz.max.pvpgames.kit.KitCreation;
import ax.xz.max.pvpgames.kit.KitName;
import ax.xz.max.pvpgames.kit.KitPersistenceException;
import ax.xz.max.pvpgames.kit.KitRepository;
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
 * <p>Validation, snapshot capture, and inventory mutation happen on the
 * calling thread (which must be the server main thread for any method that
 * touches a {@link Player}). Persistence is delegated to {@link KitRepository}.
 * Error messages are pre-formatted here so callers can surface them
 * unchanged.
 */
public final class DefaultKitService implements KitService {

    private final KitRepository repository;
    private final Clock clock;

    public DefaultKitService(KitRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Result<KitCreation, String> create(Player owner, String rawName) {
        Objects.requireNonNull(owner, "owner");

        Result<KitName, String> parsed = KitName.tryParse(rawName);
        if (parsed instanceof Result.Err<KitName, String>(String reason)) {
            return new Result.Err<>(reason);
        }
        KitName name = ((Result.Ok<KitName, String>) parsed).val();

        InventorySnapshot snapshot = InventorySnapshot.captureFrom(owner);
        if (snapshot.isEmpty()) {
            return new Result.Err<>("Your inventory is empty; nothing to save.");
        }

        Kit kit = new Kit(name, snapshot, clock.instant(), owner.getUniqueId());
        try {
            boolean replaced = repository.save(kit);
            return new Result.Ok<>(new KitCreation(kit, replaced));
        } catch (KitPersistenceException ex) {
            return new Result.Err<>("Could not save kit: " + ex.getMessage());
        }
    }

    @Override
    public Result<Kit, String> load(Player target, String rawName) {
        Objects.requireNonNull(target, "target");

        Result<KitName, String> parsed = KitName.tryParse(rawName);
        if (parsed instanceof Result.Err<KitName, String>(String reason)) {
            return new Result.Err<>(reason);
        }
        KitName name = ((Result.Ok<KitName, String>) parsed).val();

        Optional<Kit> kit = repository.find(name);
        if (kit.isEmpty()) {
            return new Result.Err<>("No kit named '" + rawName + "' exists.");
        }

        target.closeInventory();
        kit.get().contents().applyTo(target);
        return new Result.Ok<>(kit.get());
    }

    @Override
    public Result<Boolean, String> delete(String rawName) {
        Result<KitName, String> parsed = KitName.tryParse(rawName);
        if (parsed instanceof Result.Err<KitName, String>(String reason)) {
            return new Result.Err<>(reason);
        }
        KitName name = ((Result.Ok<KitName, String>) parsed).val();

        try {
            return new Result.Ok<>(repository.delete(name));
        } catch (KitPersistenceException ex) {
            return new Result.Err<>("Could not delete kit: " + ex.getMessage());
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
        Result<KitName, String> parsed = KitName.tryParse(rawName);
        if (parsed instanceof Result.Ok<KitName, String>(KitName name)) {
            return repository.find(name);
        }
        return Optional.empty();
    }
}
