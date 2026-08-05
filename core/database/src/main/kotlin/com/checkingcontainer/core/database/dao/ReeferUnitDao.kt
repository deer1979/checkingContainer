package com.checkingcontainer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Upsert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.checkingcontainer.core.database.entity.ReeferUnitEntity

@Dao
interface ReeferUnitDao {

    @Query("SELECT * FROM reefer_units WHERE containerNo = :containerNo LIMIT 1")
    suspend fun findByContainerNo(containerNo: String): ReeferUnitEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(unit: ReeferUnitEntity): Long

    /**
     * OJO: aquí NO se puede usar `@Insert(REPLACE)`.
     *
     * En SQLite, REPLACE resuelve el conflicto **borrando** la fila y volviendo a
     * insertarla, y `inspections` tiene una clave foránea a esta tabla con
     * `ON DELETE CASCADE`: cada re-inserción de un equipo se llevaba por delante
     * **todas sus inspecciones** (y con ellas el vínculo del estimado, que se
     * quedaba sin contenedor ni datos de placa).
     *
     * `@Upsert` hace INSERT o UPDATE según corresponda, sin borrar nada.
     */
    @Upsert
    suspend fun upsert(unit: ReeferUnitEntity)

    @Query("SELECT COUNT(*) FROM reefer_units")
    suspend fun count(): Int
}
